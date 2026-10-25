package com.syntaxa.khata.feature.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.core.ui.theme.KiranaCreditGreen
import com.syntaxa.khata.core.ui.theme.KiranaDebitRed
import com.syntaxa.khata.core.ui.theme.KiranaGreenPrimary
import com.syntaxa.khata.domain.model.CustomerWithBalance
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    viewModel: CustomerViewModel,
    onSelectCustomer: (Long) -> Unit,
    onOpenAddCustomerDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchInput.collectAsState()
    val activeCustomers by viewModel.activeCustomers.collectAsState()
    val archivedCustomers by viewModel.archivedCustomers.collectAsState()
    val activeFilter by viewModel.dueDateFilter.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Active, 1 = Archived

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddCustomerDialog,
                containerColor = KiranaGreenPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.action_add_customer))
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text(stringResource(R.string.search_customer)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Tab bar: Active vs Archived
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("${stringResource(R.string.customers)} (${activeCustomers.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("${stringResource(R.string.archived_customers)} (${archivedCustomers.size})") }
                )
            }

            // Due Date Filter Chips (Active Customers only)
            if (selectedTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeFilter == DueDateFilter.ALL,
                        onClick = { viewModel.setDueDateFilter(DueDateFilter.ALL) },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = activeFilter == DueDateFilter.OVERDUE,
                        onClick = { viewModel.setDueDateFilter(DueDateFilter.OVERDUE) },
                        label = { Text("⚠️ Overdue") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KiranaDebitRed.copy(alpha = 0.15f),
                            selectedLabelColor = KiranaDebitRed
                        )
                    )
                    FilterChip(
                        selected = activeFilter == DueDateFilter.DUE_TODAY,
                        onClick = { viewModel.setDueDateFilter(DueDateFilter.DUE_TODAY) },
                        label = { Text("🕒 Due Today") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFF3E0),
                            selectedLabelColor = Color(0xFFE65100)
                        )
                    )
                    FilterChip(
                        selected = activeFilter == DueDateFilter.UPCOMING,
                        onClick = { viewModel.setDueDateFilter(DueDateFilter.UPCOMING) },
                        label = { Text("📅 Upcoming") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            val currentList = if (selectedTab == 0) activeCustomers else archivedCustomers

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PeopleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No customers found matching '$searchQuery'"
                                   else if (selectedTab == 0 && activeFilter != DueDateFilter.ALL) "No customers matching $activeFilter filter"
                                   else if (selectedTab == 0) "No customers added yet"
                                   else "No archived customers",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedTab == 0 && searchQuery.isBlank() && activeFilter == DueDateFilter.ALL) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onOpenAddCustomerDialog) {
                                Text(stringResource(R.string.action_add_customer))
                            }
                        }
                    }
                }
            } else {
                val startOfToday = remember { DateTimeUtils.getStartOfDay() }
                val endOfToday = remember { DateTimeUtils.getEndOfDay() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentList, key = { it.id }) { customer ->
                        CustomerCardItem(
                            customer = customer,
                            onClick = { onSelectCustomer(customer.id) },
                            startOfToday = startOfToday,
                            endOfToday = endOfToday
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerCardItem(
    customer: CustomerWithBalance,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    startOfToday: Long = remember { DateTimeUtils.getStartOfDay() },
    endOfToday: Long = remember { DateTimeUtils.getEndOfDay() }
) {
    val owesMoney = customer.balancePaise > 0
    val hasAdvance = customer.balancePaise < 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar Circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = customer.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (customer.phone.isNotBlank()) {
                        Text(
                            text = customer.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    // Due Date Tag if present
                    if (customer.dueDate != null && owesMoney) {
                        val dueEpoch = customer.dueDate
                        val dateText = remember(dueEpoch) {
                            val localDate = Instant.ofEpochMilli(dueEpoch).atZone(ZoneId.systemDefault()).toLocalDate()
                            localDate.format(DateTimeFormatter.ofPattern("dd-MMM"))
                        }
                        val isOverdue = dueEpoch < startOfToday
                        val isDueToday = dueEpoch in startOfToday..endOfToday

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when {
                                isOverdue -> KiranaDebitRed.copy(alpha = 0.12f)
                                isDueToday -> Color(0xFFFFF3E0)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = when {
                                    isOverdue -> "Overdue: $dateText"
                                    isDueToday -> "Due Today"
                                    else -> "Due: $dateText"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = when {
                                    isOverdue -> KiranaDebitRed
                                    isDueToday -> Color(0xFFE65100)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    } else if (customer.lastTransactionAt != null) {
                        Text(
                            text = "Last: ${DateTimeUtils.formatDate(customer.lastTransactionAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Balance Tag
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        owesMoney -> "Due"
                        hasAdvance -> "Advance"
                        else -> "Settled"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        owesMoney -> KiranaDebitRed
                        hasAdvance -> KiranaCreditGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = CurrencyFormatter.formatPaise(customer.balancePaise),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        owesMoney -> KiranaDebitRed
                        hasAdvance -> KiranaCreditGreen
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
