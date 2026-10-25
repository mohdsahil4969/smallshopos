package com.syntaxa.khata

import android.content.res.Configuration
import android.content.ContextWrapper
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.util.Locale
import com.syntaxa.khata.core.preferences.UserGuidePreferences
import com.syntaxa.khata.core.security.AppLockScreen
import com.syntaxa.khata.core.security.PinSecurityManager
import com.syntaxa.khata.core.ui.navigation.Screen
import com.syntaxa.khata.core.ui.navigation.bottomNavScreens
import com.syntaxa.khata.core.ui.theme.SyntaxaKhataTheme
import com.syntaxa.khata.feature.billing.BillingViewModel
import com.syntaxa.khata.feature.customers.AddCustomerDialog
import com.syntaxa.khata.feature.customers.AddUdhaarDialog
import com.syntaxa.khata.feature.customers.CustomerListScreen
import com.syntaxa.khata.feature.customers.CustomerProfileScreen
import com.syntaxa.khata.feature.customers.CustomerViewModel
import com.syntaxa.khata.feature.customers.ReceivePaymentDialog
import com.syntaxa.khata.feature.expenses.ExpenseViewModel
import com.syntaxa.khata.feature.guide.OnboardingWalkthroughScreen
import com.syntaxa.khata.feature.guide.TermsAndConditionsScreen
import com.syntaxa.khata.feature.guide.UserGuideScreen
import com.syntaxa.khata.feature.home.HomeScreen
import com.syntaxa.khata.feature.home.HomeViewModel
import com.syntaxa.khata.feature.records.RecordsScreen
import com.syntaxa.khata.feature.reports.ReportsViewModel
import com.syntaxa.khata.feature.settings.SettingsScreen
import com.syntaxa.khata.feature.settings.SettingsViewModel
import com.syntaxa.khata.feature.splash.SplashScreen
import com.syntaxa.khata.feature.subscription.SubscriptionManager
import com.syntaxa.khata.feature.subscription.SubscriptionPaywallScreen
import com.syntaxa.khata.feature.update.AppUpdateDialog
import com.syntaxa.khata.feature.update.AppUpdateViewModel
import com.syntaxa.khata.feature.update.UpdateStatus

class MainActivity : FragmentActivity() {

    private val container by lazy { (application as KhataApplication).container }
    private val pinSecurityManager by lazy { PinSecurityManager(this) }

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory(container)
    }

    private val customerViewModel: CustomerViewModel by viewModels {
        CustomerViewModel.Factory(container)
    }

    private val billingViewModel: BillingViewModel by viewModels {
        BillingViewModel.Factory(container)
    }

    private val expenseViewModel: ExpenseViewModel by viewModels {
        ExpenseViewModel.Factory(container)
    }

    private val reportsViewModel: ReportsViewModel by viewModels {
        ReportsViewModel.Factory(container)
    }

    private val inventoryViewModel: com.syntaxa.khata.feature.inventory.InventoryViewModel by viewModels {
        com.syntaxa.khata.feature.inventory.InventoryViewModel.Factory(container)
    }

    private val supplierViewModel: com.syntaxa.khata.feature.suppliers.SupplierViewModel by viewModels {
        com.syntaxa.khata.feature.suppliers.SupplierViewModel.Factory(container)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(container, this)
    }

    private val appUpdateViewModel: AppUpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val shopProfile by homeViewModel.shopProfile.collectAsState()
            val languageCode = shopProfile?.language ?: "en"
            val appLocale = remember(languageCode) { Locale(languageCode) }

            val baseConfiguration = LocalConfiguration.current
            val localizedConfiguration = remember(baseConfiguration, appLocale) {
                Configuration(baseConfiguration).apply {
                    setLocale(appLocale)
                    setLayoutDirection(appLocale)
                }
            }

            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext, localizedConfiguration) {
                val configContext = baseContext.createConfigurationContext(localizedConfiguration)
                object : ContextWrapper(configContext) {
                    override fun getBaseContext(): android.content.Context = this@MainActivity
                }
            }

            LaunchedEffect(appLocale) {
                Locale.setDefault(appLocale)
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration,
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                SyntaxaKhataTheme {
                    val userGuidePreferences = remember { UserGuidePreferences(this@MainActivity) }
                    val subscriptionManager = remember { container.subscriptionManager }
                    var showSplashScreen by remember { mutableStateOf(true) }
                    var isAppUnlocked by remember {
                        mutableStateOf(!pinSecurityManager.isAppLockEnabled())
                    }
                    var showTermsScreen by remember {
                        mutableStateOf(!userGuidePreferences.hasAcceptedTerms())
                    }
                    var showOnboardingWalkthrough by remember {
                        mutableStateOf(userGuidePreferences.hasAcceptedTerms() && !userGuidePreferences.hasCompletedOnboarding())
                    }

                    // If lock is enabled and biometrics is allowed, launch BiometricPrompt after splash
                    LaunchedEffect(showSplashScreen, isAppUnlocked) {
                        if (!showSplashScreen && !isAppUnlocked && pinSecurityManager.isBiometricEnabled()) {
                            promptBiometrics {
                                isAppUnlocked = true
                            }
                        }
                    }

                    if (showSplashScreen) {
                        SplashScreen(
                            onFinished = {
                                showSplashScreen = false
                            }
                        )
                    } else if (!isAppUnlocked) {
                        AppLockScreen(
                            onUnlock = { isAppUnlocked = true },
                            pinSecurityManager = pinSecurityManager
                        )
                    } else if (showTermsScreen) {
                        TermsAndConditionsScreen(
                            onAccepted = {
                                userGuidePreferences.setTermsAccepted(true)
                                showTermsScreen = false
                                showOnboardingWalkthrough = !userGuidePreferences.hasCompletedOnboarding()
                            }
                        )
                    } else if (showOnboardingWalkthrough) {
                        OnboardingWalkthroughScreen(
                            onComplete = {
                                userGuidePreferences.setOnboardingCompleted(true)
                                showOnboardingWalkthrough = false
                            }
                        )
                    } else {
                        MainAppScaffold(
                            homeViewModel = homeViewModel,
                            customerViewModel = customerViewModel,
                            billingViewModel = billingViewModel,
                            expenseViewModel = expenseViewModel,
                            reportsViewModel = reportsViewModel,
                            inventoryViewModel = inventoryViewModel,
                            supplierViewModel = supplierViewModel,
                            settingsViewModel = settingsViewModel,
                            appUpdateViewModel = appUpdateViewModel,
                            subscriptionManager = subscriptionManager,
                            onReplayWalkthrough = {
                                showOnboardingWalkthrough = true
                            }
                        )
                    }
                }
            }
        }
    }

    private fun promptBiometrics(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Fall back gracefully to PIN
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_app))
            .setSubtitle(getString(R.string.app_name))
            .setNegativeButtonText(getString(R.string.enter_pin))
            .build()

        prompt.authenticate(promptInfo)
    }
}

@Composable
fun MainAppScaffold(
    homeViewModel: HomeViewModel,
    customerViewModel: CustomerViewModel,
    billingViewModel: BillingViewModel,
    expenseViewModel: ExpenseViewModel,
    reportsViewModel: ReportsViewModel,
    inventoryViewModel: com.syntaxa.khata.feature.inventory.InventoryViewModel,
    supplierViewModel: com.syntaxa.khata.feature.suppliers.SupplierViewModel,
    settingsViewModel: SettingsViewModel,
    appUpdateViewModel: AppUpdateViewModel,
    subscriptionManager: SubscriptionManager? = null,
    onReplayWalkthrough: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showAddUdhaarDialog by remember { mutableStateOf(false) }
    var showReceivePaymentDialog by remember { mutableStateOf(false) }
    var dialogPreselectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var recordsInitialTab by remember { mutableIntStateOf(0) }

    val activeCustomers by customerViewModel.activeCustomers.collectAsState()
    val updateState by appUpdateViewModel.updateState.collectAsState()

    // Silent background check on app startup if auto-check is enabled
    LaunchedEffect(Unit) {
        if (appUpdateViewModel.isAutoCheckEnabled()) {
            appUpdateViewModel.checkForUpdate(isManual = false)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavScreens.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleRes)) },
                        label = {
                            Text(
                                text = stringResource(screen.titleRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = isSelected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                // 1. Home
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToKhata = { navController.navigate(Screen.Khata.route) },
                        onNavigateToBilling = {
                            recordsInitialTab = 0
                            navController.navigate(Screen.Records.route)
                        },
                        onNavigateToExpenses = {
                            recordsInitialTab = 1
                            navController.navigate(Screen.Records.route)
                        },
                        onOpenAddCustomerDialog = { showAddCustomerDialog = true },
                        onOpenAddUdhaarDialog = {
                            dialogPreselectedCustomerId = null
                            showAddUdhaarDialog = true
                        },
                        onOpenReceivePaymentDialog = {
                            dialogPreselectedCustomerId = null
                            showReceivePaymentDialog = true
                        },
                        subscriptionManager = subscriptionManager,
                        onNavigateToPaywall = { navController.navigate("subscription_paywall") },
                        onOpenUserGuide = { navController.navigate("user_guide") }
                    )
                }

                // 2. Khata
                composable(Screen.Khata.route) {
                    CustomerListScreen(
                        viewModel = customerViewModel,
                        onSelectCustomer = { customerId ->
                            navController.navigate("customer_profile/$customerId")
                        },
                        onOpenAddCustomerDialog = { showAddCustomerDialog = true }
                    )
                }

                // Customer Profile Screen
                composable(
                    route = "customer_profile/{customerId}",
                    arguments = listOf(navArgument("customerId") { type = NavType.LongType })
                ) { backStack ->
                    val customerId = backStack.arguments?.getLong("customerId") ?: 0L
                    CustomerProfileScreen(
                        customerId = customerId,
                        viewModel = customerViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenAddUdhaar = { cId ->
                            dialogPreselectedCustomerId = cId
                            showAddUdhaarDialog = true
                        },
                        onOpenReceivePayment = { cId ->
                            dialogPreselectedCustomerId = cId
                            showReceivePaymentDialog = true
                        }
                    )
                }

                // 3. Records (Billing + Expenses + Inventory + Suppliers + Reports consolidated)
                composable(Screen.Records.route) {
                    RecordsScreen(
                        billingViewModel = billingViewModel,
                        expenseViewModel = expenseViewModel,
                        reportsViewModel = reportsViewModel,
                        inventoryViewModel = inventoryViewModel,
                        supplierViewModel = supplierViewModel,
                        subscriptionManager = subscriptionManager,
                        onNavigateToPaywall = { navController.navigate("subscription_paywall") },
                        initialTab = recordsInitialTab
                    )
                }

                // 4. More (Settings + Printers + App Lock + Backup & Restore + In-App Update)
                composable(Screen.More.route) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        updateViewModel = appUpdateViewModel,
                        subscriptionManager = subscriptionManager,
                        onNavigateToPaywall = { navController.navigate("subscription_paywall") },
                        onOpenUserGuide = { navController.navigate("user_guide") },
                        onOpenTerms = { navController.navigate("terms_and_conditions") },
                        onReplayWalkthrough = onReplayWalkthrough
                    )
                }

                // 5. User Guide & Shop Manual
                composable("user_guide") {
                    UserGuideScreen(
                        onBack = { navController.popBackStack() },
                        onReplayWalkthrough = onReplayWalkthrough,
                        onOpenTerms = { navController.navigate("terms_and_conditions") }
                    )
                }

                // 6. Terms of Service & Software Disclaimer (Review Mode)
                composable("terms_and_conditions") {
                    TermsAndConditionsScreen(
                        onAccepted = { navController.popBackStack() },
                        isReviewMode = true,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 7. PRO Subscription Paywall (₹49/month)
                composable("subscription_paywall") {
                    subscriptionManager?.let { sm ->
                        SubscriptionPaywallScreen(
                            subscriptionManager = sm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }

            // Dialogs
            if (showAddCustomerDialog) {
                AddCustomerDialog(
                    onDismiss = { showAddCustomerDialog = false },
                    onSave = { name, phone, notes ->
                        customerViewModel.addCustomer(
                            name = name,
                            phone = phone,
                            notes = notes,
                            onSuccess = {
                                showAddCustomerDialog = false
                            },
                            onError = { }
                        )
                    },
                    checkDuplicatePhone = { phone ->
                        customerViewModel.isPhoneDuplicate(phone)
                    }
                )
            }

            if (showAddUdhaarDialog) {
                AddUdhaarDialog(
                    preselectedCustomerId = dialogPreselectedCustomerId,
                    customers = activeCustomers,
                    onDismiss = { showAddUdhaarDialog = false },
                    onSave = { customerId, amountPaise, desc ->
                        customerViewModel.recordUdhaar(
                            customerId = customerId,
                            amountPaise = amountPaise,
                            description = desc,
                            onSuccess = { showAddUdhaarDialog = false },
                            onError = { }
                        )
                    }
                )
            }

            if (showReceivePaymentDialog) {
                ReceivePaymentDialog(
                    preselectedCustomerId = dialogPreselectedCustomerId,
                    customers = activeCustomers,
                    onDismiss = { showReceivePaymentDialog = false },
                    onSave = { customerId, amountPaise, method, desc ->
                        customerViewModel.recordPayment(
                            customerId = customerId,
                            amountPaise = amountPaise,
                            paymentMethod = method,
                            description = desc,
                            onSuccess = { showReceivePaymentDialog = false },
                            onError = { }
                        )
                    }
                )
            }

            // In-App One-Tap Update Dialog
            if (updateState.updateInfo != null && (
                updateState.status == UpdateStatus.AVAILABLE ||
                updateState.status == UpdateStatus.DOWNLOADING ||
                updateState.status == UpdateStatus.VERIFYING ||
                updateState.status == UpdateStatus.PERMISSION_REQUIRED ||
                updateState.status == UpdateStatus.READY_TO_INSTALL ||
                updateState.status == UpdateStatus.ERROR
            )) {
                AppUpdateDialog(
                    updateState = updateState,
                    onUpdateClick = { appUpdateViewModel.startOneClickUpdate() },
                    onDismiss = { appUpdateViewModel.dismissUpdate() },
                    onRequestPermission = { appUpdateViewModel.requestInstallPermission() },
                    onResumeInstall = { appUpdateViewModel.resumeInstallAfterPermission() }
                )
            }
        }
    }
}
