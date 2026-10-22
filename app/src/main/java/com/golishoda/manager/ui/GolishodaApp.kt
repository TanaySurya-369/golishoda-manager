package com.golishoda.manager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.golishoda.manager.data.LedgerRepository
import com.golishoda.manager.data.ShopEntity
import com.golishoda.manager.data.TransactionEntity
import com.golishoda.manager.domain.LedgerRules
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.golishoda.manager.export.CsvWriter
import android.content.Context
import android.net.Uri
import com.golishoda.manager.backup.BackupManager

private enum class Destination(val route: String, val label: String) {
    HOME("home", "Home"), SHOPS("shops", "Shops"), ENTRY("entry", "Add Entry"), SUPPLY("supply", "Daily Supply"), REPORTS("reports", "Reports"), SETTINGS("settings", "Settings")
}
private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GolishodaApp(repository: LedgerRepository) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingExport by remember { mutableStateOf("ledger") }
    var entryType by remember { mutableStateOf("DELIVERY") }
    var message by remember { mutableStateOf<String?>(null) }
    val shops by repository.shops.collectAsStateWithLifecycle(emptyList())
    val transactions by repository.transactions.collectAsStateWithLifecycle(emptyList())
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { runCatching { writeCsv(context, uri, pendingExport, shops, transactions) }.onFailure { message = it.message ?: "Export failed." }.onSuccess { message = "CSV exported." } }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { runCatching { BackupManager.write(repository, context.contentResolver, uri) }.onFailure { message = it.message ?: "Backup failed." }.onSuccess { message = "Backup saved. Shop photos are not included." } }
    }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
        topBar = { TopAppBar(title = { Text("Golishoda Manager", fontWeight = FontWeight.SemiBold) }) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val icon = when (destination) {
                        Destination.HOME -> Icons.Default.Home
                        Destination.SHOPS -> Icons.Default.Store
                        Destination.ENTRY -> Icons.Default.Add
                        Destination.SUPPLY -> Icons.Default.Inventory2
                        Destination.REPORTS -> Icons.Default.Assessment
                        Destination.SETTINGS -> Icons.Default.Settings
                    }
                    NavigationBarItem(selected = currentRoute == destination.route, onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }, icon = { Icon(icon, contentDescription = destination.label) }, label = { Text(destination.label) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = Destination.HOME.route) {
                composable(Destination.HOME.route) {
                    HomeScreen(shops, transactions,
                        onAdd = { entryType = it; navController.navigate(Destination.ENTRY.route) { launchSingleTop = true } },
                        onShops = { navController.navigate(Destination.SHOPS.route) { launchSingleTop = true } },
                        onExport = { kind, name -> pendingExport = kind; exportLauncher.launch(name) })
                }
                composable(Destination.SHOPS.route) {
                    ShopsScreen(shops, transactions, repository, scope,
                        onEntry = { entryType = it; navController.navigate(Destination.ENTRY.route) { launchSingleTop = true } },
                        onExport = { kind, name -> pendingExport = kind; exportLauncher.launch(name) })
                }
                composable(Destination.ENTRY.route) {
                    EntryScreen(shops, repository, scope, entryType, onTypeChange = { entryType = it }, onSaved = { message = it })
                }
                composable(Destination.SUPPLY.route) { SupplyScreen(shops, transactions, onExport = { kind, name -> pendingExport = kind; exportLauncher.launch(name) }) }
                composable(Destination.REPORTS.route) { ReportsScreen(shops, transactions, onExport = { kind, name -> pendingExport = kind; exportLauncher.launch(name) }) }
                composable(Destination.SETTINGS.route) { SettingsScreen(onBackup = { backupLauncher.launch("golishoda-backup.json") }, onRestore = { restoreLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }, onExport = { kind, name -> pendingExport = kind; exportLauncher.launch(name) }) }
            }
            message?.let { Text(it, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.primary) }
            restoreUri?.let { uri ->
                var restoring by remember(uri) { mutableStateOf(false) }
                AlertDialog(onDismissRequest = { if (!restoring) restoreUri = null }, title = { Text("Replace local data?") }, text = { Text("Restoring replaces all current shops and transactions. Create a backup first if you need to keep the existing data. Shop photos are not included.") }, confirmButton = { TextButton(enabled = !restoring, onClick = { restoring = true; scope.launch { runCatching { context.contentResolver.openInputStream(uri)?.use { BackupManager.restore(repository, it) } ?: error("Cannot open the selected backup.") }.onSuccess { message = "Backup restored."; restoreUri = null }.onFailure { message = it.message ?: "Restore failed." }; restoring = false } }) { Text(if (restoring) "Restoring…" else "Replace data") } }, dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } })
            }
        }
    }
}

@Composable
private fun HomeScreen(shops: List<ShopEntity>, transactions: List<TransactionEntity>, onAdd: (String) -> Unit, onShops: () -> Unit, onExport: (String, String) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    val daily = transactions.filter { it.transactionDate == date.toString() }
    val balances = shops.associate { shop -> shop.id to transactions.filter { it.shopId == shop.id }.sumOf { ledgerDelta(it) } }
    val stock = shops.sumOf { shop -> transactions.filter { it.shopId == shop.id }.sumOf { it.deliveredQuantity.toLong() - it.returnedQuantity } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Your business at a glance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        DateSelector(date, { date = it })
        val supplied = daily.filter { it.deliveredQuantity > 0 }.map { it.shopId }.distinct().size
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Shops supplied", supplied.toString(), Modifier.weight(1f))
            MetricCard("Bottles in", daily.sumOf { it.deliveredQuantity }.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Returns", daily.sumOf { it.returnedQuantity }.toString(), Modifier.weight(1f))
            MetricCard("Net movement", daily.sumOf { it.deliveredQuantity - it.returnedQuantity }.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Collected", LedgerRules.formatMoney(daily.sumOf { it.paymentAmountPaise }), Modifier.weight(1f))
            MetricCard("Delivery value", LedgerRules.formatMoney(daily.sumOf { it.deliveredQuantity.toLong() * it.unitPricePaise }), Modifier.weight(1f))
        }
        MetricCard("Outstanding due", LedgerRules.formatMoney(balances.values.filter { it > 0 }.sum()), Modifier.fillMaxWidth())
        MetricCard("Advance credit", LedgerRules.formatMoney(balances.values.filter { it < 0 }.sumOf { -it }), Modifier.fillMaxWidth())
        MetricCard("Bottles with shops", stock.toString(), Modifier.fillMaxWidth())
        Text("Quick actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAdd("DELIVERY") }) { Text("Add delivery") }
            OutlinedButton(onClick = { onAdd("RETURN") }) { Text("Record return") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAdd("PAYMENT") }) { Text("Record payment") }
            OutlinedButton(onClick = onShops) { Text("Add shop") }
        }
        OutlinedButton(onClick = { onExport("daily", "daily-supply.csv") }) { Text("Export daily CSV") }
        if (shops.isEmpty()) EmptyState("No shops yet", "Add your first shop to start tracking deliveries.")
    }
}

@Composable
private fun ShopsScreen(shops: List<ShopEntity>, transactions: List<TransactionEntity>, repository: LedgerRepository, scope: CoroutineScope, onEntry: (String) -> Unit, onExport: (String, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var selectedShop by remember { mutableStateOf<ShopEntity?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    val visible = shops.filter { it.isArchived == showArchived && (it.name.contains(query, true) || it.nickname.contains(query, true) || it.phone.contains(query, true)) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Shops", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Button(onClick = { addError = null; showAdd = true }) { Text("+ Add shop") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), label = { Text("Search name or phone") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Active") })
            FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("Archived") })
        }
        OutlinedButton(onClick = { onExport("shops", "shops.csv") }) { Text("Export shop list CSV") }
        if (visible.isEmpty()) EmptyState("No matching shops", "Add a shop or change your search.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { shop ->
                val history = transactions.filter { it.shopId == shop.id }
                val balance = history.sumOf { ledgerDelta(it) }
                val stock = history.sumOf { it.deliveredQuantity - it.returnedQuantity }
                Card(onClick = { selectedShop = shop }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(shop.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (shop.nickname.isNotBlank()) Text(shop.nickname, style = MaterialTheme.typography.bodySmall)
                        Text("${shop.phone.ifBlank { shop.address.ifBlank { "No contact details" } }} · Stock: $stock")
                        Text(if (balance >= 0) "Due ${LedgerRules.formatMoney(balance)}" else "Advance ${LedgerRules.formatMoney(-balance)}", color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (showAdd) ShopForm(error = addError, onDismiss = { showAdd = false }, onSave = { name, nickname, phone, address, price ->
        scope.launch {
            runCatching { repository.saveShop(ShopEntity(name = name, nickname = nickname, phone = phone, address = address, defaultUnitPricePaise = price)) }
                .onSuccess { showAdd = false; addError = null }
                .onFailure { addError = it.message ?: "Could not save shop." }
        }
    })
    selectedShop?.let { shop ->
        val history = transactions.filter { it.shopId == shop.id }
        AlertDialog(onDismissRequest = { selectedShop = null }, title = { Text(shop.name) }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("${shop.phone}\n${shop.address}\nStock: ${history.sumOf { it.deliveredQuantity - it.returnedQuantity }} bottles")
                val balance = history.sumOf { ledgerDelta(it) }
                Text(if (balance >= 0) "Outstanding: ${LedgerRules.formatMoney(balance)}" else "Advance credit: ${LedgerRules.formatMoney(-balance)}")
                Text("\nTransaction history", fontWeight = FontWeight.Bold)
                history.forEach { tx -> Text("${tx.transactionDate} · ${tx.transactionType} · +${tx.deliveredQuantity} / −${tx.returnedQuantity} · ${LedgerRules.formatMoney(tx.paymentAmountPaise)} received") }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { selectedShop = null; onEntry("DELIVERY") }) { Text("Delivery") }
                    TextButton(onClick = { selectedShop = null; onEntry("PAYMENT") }) { Text("Payment") }
                    TextButton(onClick = { selectedShop = null; onEntry("RETURN") }) { Text("Return") }
                }
                TextButton(onClick = {
                    scope.launch { repository.saveShop(shop.copy(isArchived = !shop.isArchived)) }
                    selectedShop = null
                }) { Text(if (shop.isArchived) "Restore shop" else "Archive shop") }
            }
        }, confirmButton = { TextButton(onClick = { selectedShop = null }) { Text("Close") } })
    }
}

@Composable
private fun ShopForm(error: String?, onDismiss: () -> Unit, onSave: (String, String, String, String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }; var nickname by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var price by remember { mutableStateOf("0") }; var inputError by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add shop") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Shop name *") }, singleLine = true)
            OutlinedTextField(nickname, { nickname = it }, label = { Text("Nickname (optional)") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(address, { address = it }, label = { Text("Address") })
            OutlinedTextField(price, { price = it }, label = { Text("Default price per bottle (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching { require(name.isNotBlank()) { "Shop name is required." }; onSave(name.trim(), nickname.trim(), phone.trim(), address.trim(), LedgerRules.rupeesToPaise(price)) }
            .onFailure { inputError = it.message }
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryScreen(shops: List<ShopEntity>, repository: LedgerRepository, scope: CoroutineScope, initialType: String, onTypeChange: (String) -> Unit, onSaved: (String) -> Unit) {
    var shopId by remember { mutableStateOf<Long?>(null) }; var date by remember { mutableStateOf(LocalDate.now()) }; var type by remember(initialType) { mutableStateOf(initialType) }
    var delivered by remember { mutableStateOf("") }; var returned by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var payment by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }; var saving by remember { mutableStateOf(false) }
    val activeShops = shops.filter { !it.isArchived }
    LaunchedEffect(shopId, activeShops) { activeShops.firstOrNull { it.id == shopId }?.let { price = "${it.defaultUnitPricePaise / 100}.${(it.defaultUnitPricePaise % 100).toString().padStart(2, '0')}" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Add entry", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (activeShops.isEmpty()) EmptyState("Add a shop first", "Create a shop before recording a delivery, return, or payment.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("DELIVERY" to "Delivery", "RETURN" to "Return", "PAYMENT" to "Payment").forEach { (value, label) ->
                FilterChip(selected = type == value, onClick = { type = value; onTypeChange(value); error = null }, label = { Text(label) })
            }
        }
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(activeShops.firstOrNull { it.id == shopId }?.name.orEmpty(), {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Shop *") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                activeShops.forEach { shop -> DropdownMenuItem(text = { Text(shop.name) }, onClick = { shopId = shop.id; expanded = false }) }
            }
        }
        DateSelector(date, { date = it })
        if (type != "PAYMENT") OutlinedTextField(delivered, { delivered = it }, label = { Text("Filled bottles delivered") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        if (type != "PAYMENT") OutlinedTextField(returned, { returned = it }, label = { Text("Empty bottles returned") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        if (type != "RETURN") OutlinedTextField(price, { price = it }, label = { Text("Price per bottle (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(payment, { payment = it }, label = { Text("Payment received (₹, optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
        Text("Returns are credited at the recorded bottle price. Payments reduce the running balance; excess becomes advance credit.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !saving && activeShops.isNotEmpty(), onClick = {
            val selectedShop = shopId
            if (selectedShop == null) { error = "Select a shop."; return@Button }
            saving = true; error = null
            scope.launch {
                runCatching {
                    fun quantity(value: String, label: String): Int = value.ifBlank { "0" }.toIntOrNull()?.also { require(it >= 0) { "$label cannot be negative." } } ?: if (value.isBlank()) 0 else error("Enter a valid $label quantity.")
                    val deliveryCount = if (type == "PAYMENT") 0 else quantity(delivered, "delivery")
                    val returnCount = if (type == "PAYMENT") 0 else quantity(returned, "return")
                    val selected = activeShops.first { it.id == selectedShop }
                    val unitPrice = if (type == "RETURN") selected.defaultUnitPricePaise else LedgerRules.rupeesToPaise(price.ifBlank { "0" })
                    val received = LedgerRules.rupeesToPaise(payment.ifBlank { "0" })
                    repository.addEntry(selectedShop, date, type, deliveryCount, returnCount, unitPrice, received, note)
                }.onSuccess { onSaved("Entry saved successfully."); delivered = ""; returned = ""; payment = ""; note = "" }
                    .onFailure { error = it.message ?: "Could not save entry." }
                saving = false
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Saving…" else "Save entry") }
    }
}

@Composable
private fun SupplyScreen(shops: List<ShopEntity>, transactions: List<TransactionEntity>, onExport: (String, String) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    val daily = transactions.filter { it.transactionDate == date.toString() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Daily Supply", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        DateSelector(date, { date = it })
        val supplied = daily.filter { it.deliveredQuantity > 0 }.map { it.shopId }.distinct().size
        Text("$supplied shops supplied · ${daily.sumOf { it.deliveredQuantity }} delivered · ${daily.sumOf { it.returnedQuantity }} returned")
        Text("Delivery value: ${LedgerRules.formatMoney(daily.sumOf { it.deliveredQuantity.toLong() * it.unitPricePaise })} · Collected: ${LedgerRules.formatMoney(daily.sumOf { it.paymentAmountPaise })}")
        Text("Return credit: ${LedgerRules.formatMoney(daily.sumOf { it.returnedQuantity.toLong() * it.unitPricePaise })}", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { onExport("daily", "daily-supply.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export daily supply CSV") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(daily, key = { it.id }) { tx ->
                val shop = shops.firstOrNull { it.id == tx.shopId }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    Text(shop?.name ?: "Shop", fontWeight = FontWeight.SemiBold)
                    Text("${tx.transactionType} · Delivered ${tx.deliveredQuantity} · Returned ${tx.returnedQuantity} · Net ${tx.deliveredQuantity - tx.returnedQuantity}")
                    Text("Price ${LedgerRules.formatMoney(tx.unitPricePaise)} · Value ${LedgerRules.formatMoney(tx.deliveredQuantity.toLong() * tx.unitPricePaise)} · Paid ${LedgerRules.formatMoney(tx.paymentAmountPaise)}")
                    if (tx.note.isNotBlank()) Text(tx.note)
                } }
            }
        }
        if (daily.isEmpty()) EmptyState("No activity on this date", "Choose another date or add an entry.")
    }
}

@Composable
private fun ReportsScreen(shops: List<ShopEntity>, transactions: List<TransactionEntity>, onExport: (String, String) -> Unit) {
    var start by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }; var end by remember { mutableStateOf(LocalDate.now()) }
    val rows = if (end.isBefore(start)) emptyList() else transactions.filter { val d = LocalDate.parse(it.transactionDate); !d.isBefore(start) && !d.isAfter(end) }
    val deliveredValue = rows.sumOf { it.deliveredQuantity.toLong() * it.unitPricePaise }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Reports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("From"); DateSelector(start, { start = it })
        Text("To"); DateSelector(end, { end = it })
        if (end.isBefore(start)) Text("The end date must not be before the start date.", color = MaterialTheme.colorScheme.error)
        MetricCard("Shops supplied", rows.filter { it.deliveredQuantity > 0 }.map { it.shopId }.distinct().size.toString(), Modifier.fillMaxWidth())
        MetricCard("Deliveries", rows.sumOf { it.deliveredQuantity }.toString(), Modifier.fillMaxWidth())
        MetricCard("Returns", rows.sumOf { it.returnedQuantity }.toString(), Modifier.fillMaxWidth())
        MetricCard("Delivery value (not cash received)", LedgerRules.formatMoney(deliveredValue), Modifier.fillMaxWidth())
        MetricCard("Return credits", LedgerRules.formatMoney(rows.sumOf { it.returnedQuantity.toLong() * it.unitPricePaise }), Modifier.fillMaxWidth())
        MetricCard("Collections received", LedgerRules.formatMoney(rows.sumOf { it.paymentAmountPaise }), Modifier.fillMaxWidth())
        OutlinedButton(onClick = { onExport("ledger", "ledger.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export ledger CSV") }
        OutlinedButton(onClick = { onExport("outstanding", "outstanding.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export outstanding CSV") }
        OutlinedButton(onClick = { onExport("collections", "collections.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export collections CSV") }
        Text("Outstanding balances", style = MaterialTheme.typography.titleMedium)
        shops.filter { !it.isArchived }.sortedByDescending { shop -> transactions.filter { it.shopId == shop.id }.sumOf { ledgerDelta(it) }.coerceAtLeast(0) }.forEach { shop ->
            val balance = transactions.filter { it.shopId == shop.id }.sumOf { ledgerDelta(it) }
            if (balance > 0) Text("${shop.name} — ${LedgerRules.formatMoney(balance)}")
        }
    }
}

@Composable
private fun SettingsScreen(
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onExport: (String, String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Data protection", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("Create data backup") }
        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("Restore from backup") }
        Text("Restore replaces all local shops and transactions. Create a backup first if you need to keep current data.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Export data", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onExport("shops", "shops.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export shop list CSV") }
        OutlinedButton(onClick = { onExport("ledger", "ledger.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export ledger CSV") }
        OutlinedButton(onClick = { onExport("outstanding", "outstanding.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export balances CSV") }
        OutlinedButton(onClick = { onExport("collections", "collections.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export collections CSV") }
    }
}

@Composable
private fun DateSelector(date: LocalDate, onChange: (LocalDate) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onChange(date.minusDays(1)) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("‹") }
        Text(date.format(dateFormat), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange(date.plusDays(1)) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("›") }
        TextButton(onClick = { onChange(LocalDate.now()) }) { Text("Today") }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(14.dp)) { Text(title, style = MaterialTheme.typography.bodySmall); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodyMedium) }
}

private fun ledgerDelta(tx: TransactionEntity): Long {
    val value = Math.multiplyExact(tx.deliveredQuantity.toLong(), tx.unitPricePaise)
    val credit = Math.multiplyExact(tx.returnedQuantity.toLong(), tx.unitPricePaise)
    return Math.subtractExact(value, Math.addExact(credit, tx.paymentAmountPaise))
}

private fun writeCsv(context: Context, uri: Uri, kind: String, shops: List<ShopEntity>, transactions: List<TransactionEntity>) {
    val output = requireNotNull(context.contentResolver.openOutputStream(uri)) { "Cannot open the selected file." }
    output.bufferedWriter().use { writer ->
        val rows = transactions.mapNotNull { tx -> shops.firstOrNull { it.id == tx.shopId }?.let { it to tx } }
        when (kind) {
            "shops" -> {
                writer.appendLine(CsvWriter.row(listOf("Shop", "Nickname", "Phone", "Address", "Current bottles", "Balance (INR)")))
                shops.forEach { shop ->
                    val history = rows.filter { it.first.id == shop.id }.map { it.second }
                    val balance = history.sumOf(::ledgerDelta)
                    writer.appendLine(CsvWriter.row(listOf(shop.name, shop.nickname, shop.phone, shop.address, history.sumOf { it.deliveredQuantity - it.returnedQuantity }.toString(), LedgerRules.formatMoney(balance))))
                }
            }
            "daily" -> {
                writer.appendLine(CsvWriter.row(listOf("Date", "Shop", "Type", "Delivered", "Returned", "Unit price", "Delivery value", "Return credit", "Payment received", "Note")))
                rows.forEach { (shop, tx) -> writer.appendLine(CsvWriter.row(listOf(tx.transactionDate, shop.name, tx.transactionType, tx.deliveredQuantity.toString(), tx.returnedQuantity.toString(), LedgerRules.formatMoney(tx.unitPricePaise), LedgerRules.formatMoney(tx.deliveredQuantity.toLong() * tx.unitPricePaise), LedgerRules.formatMoney(tx.returnedQuantity.toLong() * tx.unitPricePaise), LedgerRules.formatMoney(tx.paymentAmountPaise), tx.note))) }
            }
            "outstanding" -> {
                writer.appendLine(CsvWriter.row(listOf("Shop", "Outstanding due", "Advance credit")))
                shops.forEach { shop -> val balance = rows.filter { it.first.id == shop.id }.sumOf { ledgerDelta(it.second) }; if (balance != 0L) writer.appendLine(CsvWriter.row(listOf(shop.name, LedgerRules.formatMoney(balance.coerceAtLeast(0)), LedgerRules.formatMoney((-balance).coerceAtLeast(0))))) }
            }
            else -> {
                writer.appendLine(CsvWriter.row(listOf("Date", "Shop", "Type", "Delivered", "Returned", "Delivery value", "Return credit", "Payment received", "Note")))
                rows.forEach { (shop, tx) ->
                    if (kind != "collections" || tx.paymentAmountPaise > 0) writer.appendLine(CsvWriter.row(listOf(tx.transactionDate, shop.name, tx.transactionType, tx.deliveredQuantity.toString(), tx.returnedQuantity.toString(), LedgerRules.formatMoney(tx.deliveredQuantity.toLong() * tx.unitPricePaise), LedgerRules.formatMoney(tx.returnedQuantity.toLong() * tx.unitPricePaise), LedgerRules.formatMoney(tx.paymentAmountPaise), tx.note)))
                }
            }
        }
    }
}
