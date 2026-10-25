package com.syntaxa.khata.feature.guide

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.theme.KiranaCreditGreen
import com.syntaxa.khata.core.ui.theme.KiranaGreenPrimary

data class WalkthroughStep(
    val stepNumber: Int,
    val icon: ImageVector,
    val tagEn: String,
    val tagHi: String,
    val titleEn: String,
    val titleHi: String,
    val subtitleEn: String,
    val subtitleHi: String,
    val keyPointsEn: List<String>,
    val keyPointsHi: List<String>,
    val proTipEn: String,
    val proTipHi: String
)

val walkthroughSteps = listOf(
    WalkthroughStep(
        stepNumber = 1,
        icon = Icons.Default.Storefront,
        tagEn = "100% OFFLINE FIRST",
        tagHi = "100% ऑफलाइन बहीखाता",
        titleEn = "Welcome to Syntaxa Khata",
        titleHi = "सिंटैक्सा खाता में आपका स्वागत है",
        subtitleEn = "Engineered specifically for Indian kirana & retail shops. Zero cloud dependencies, zero subscription traps, and total data privacy.",
        subtitleHi = "भारतीय किराना और खुदरा दुकानदारों के लिए विशेष रूप से निर्मित। बिना इंटरनेट, बिना किसी शुल्क और पूर्ण डेटा गोपनीयता।",
        keyPointsEn = listOf(
            "Works completely offline without requiring internet or cloud servers",
            "Exact integer paisa math guarantees zero rounding errors",
            "Customer data is stored securely on your phone and never shared"
        ),
        keyPointsHi = listOf(
            "बिना इंटरनेट या क्लाउड सर्वर के पूरी तरह ऑफलाइन काम करता है",
            "पैसे-पैसे का सटीक हिसाब, दशमलव की कोई गलती नहीं",
            "ग्राहकों का डेटा आपके फोन में सुरक्षित रहता है, कहीं शेयर नहीं होता"
        ),
        proTipEn = "You never have to worry about network issues or slow internet while serving customers at your counter.",
        proTipHi = "काउंटर पर ग्राहक के सामने इंटरनेट या नेटवर्क की समस्या की कोई चिंता नहीं।"
    ),
    WalkthroughStep(
        stepNumber = 2,
        icon = Icons.Default.FlashOn,
        tagEn = "FAST COUNTER ACTIONS",
        tagHi = "तेज़ काउंटर एंट्री",
        titleEn = "Home & Quick 1-Tap Actions",
        titleHi = "होम स्क्रीन और 1-टैप त्वरित कार्य",
        subtitleEn = "Serve rushing customers in seconds with prominent 1-tap tiles right on your home screen.",
        subtitleHi = "दुकान की भीड़ में भी बिना समय गंवाए होम स्क्रीन से सीधे 1-टैप में लेन-देन दर्ज करें।",
        keyPointsEn = listOf(
            "Give Udhaar (उधार दें) and Collect Payment (जमा लें) in under 3 taps",
            "Instant Quick Bill and Categorized Shop Expense logging",
            "Live counters show Today's Cash, UPI, Expenses, and Total Market Udhaar"
        ),
        keyPointsHi = listOf(
            "मात्र 3 टैप में उधार दें और भुगतान जमा करें",
            "तुरंत बिल बनाएं और रोज़मर्रा का दुकान खर्च दर्ज करें",
            "आज का नकद, UPI, खर्च और कुल बाज़ार उधारी स्क्रीन पर तुरंत दिखती है"
        ),
        proTipEn = "Check 'Net Cash Movement' on the home screen before closing shop to see your actual net cash inflow.",
        proTipHi = "दुकान बंद करने से पहले होम स्क्रीन पर 'शुद्ध नकद आवागमन' देखकर दिन की कुल नकद कमाई जानें।"
    ),
    WalkthroughStep(
        stepNumber = 3,
        icon = Icons.AutoMirrored.Filled.MenuBook,
        tagEn = "CREDIT & RECOVERY",
        tagHi = "उधार व वसूली",
        titleEn = "Customer Khata & Smart Due Dates",
        titleHi = "ग्राहक खाता व देय तिथियां",
        subtitleEn = "Recover debts faster with clear balances, overdue tracking, and polite WhatsApp reminders.",
        subtitleHi = "स्पष्ट बहीखाता, देय तिथि फिल्टर और विनम्र वॉट्सऐप संदेशों से उधारी जल्दी वसूलें।",
        keyPointsEn = listOf(
            "Red balance means customer owes you; Green means advance payment",
            "Filter customers by 'Overdue' (समय सीमा समाप्त) or 'Due Today' (आज देय)",
            "Send polite WhatsApp reminders with your shop's UPI QR code in English or Hindi",
            "Immutable audit trail: Mistakes can be corrected with banking-grade reversals"
        ),
        keyPointsHi = listOf(
            "लाल रंग का मतलब ग्राहक पर बाकी है; हरा रंग मतलब अग्रिम जमा",
            "'समय सीमा समाप्त' और 'आज देय' फिल्टर से वसूली में प्राथमिकता तय करें",
            "दुकान के UPI QR कोड के साथ हिंदी या अंग्रेजी में वॉट्सऐप तकादा भेजें",
            "गलत प्रविष्टि को सुधारने के लिए 'निरस्त' (Reversal) का विकल्प उपलब्ध है"
        ),
        proTipEn = "Set a Due Date (+3, +7, +15, +30 days) whenever giving udhaar so the app alerts you on collection day.",
        proTipHi = "उधार देते समय हमेशा देय तिथि चुनें ताकि तारीख आने पर ऐप आपको याद दिला सके।"
    ),
    WalkthroughStep(
        stepNumber = 4,
        icon = Icons.AutoMirrored.Filled.ReceiptLong,
        tagEn = "ACCURATE BILLING",
        tagHi = "सटीक बिलिंग व स्टॉक",
        titleEn = "Smart Billing & Inventory Deduction",
        titleHi = "स्मार्ट बिलिंग और स्वचालित स्टॉक",
        subtitleEn = "Issue professional bills in seconds and keep item inventory strictly accurate.",
        subtitleHi = "सेकंडों में पक्का बिल बनाएं और दुकान का स्टॉक अपने आप अपडेट रखें।",
        keyPointsEn = listOf(
            "Quick Total Mode: Enter simple amount directly without selecting items",
            "Itemized Mode: Pick items from product catalog with quantity and discounts",
            "Split payments seamlessly: Accept Cash, UPI, and put balance on Customer Udhaar",
            "Automatic stock deduction: Items billed are atomically deducted from inventory"
        ),
        keyPointsHi = listOf(
            "क्विक बिल: बिना आइटम चुने सीधे कुल रकम दर्ज करके बिल बनाएं",
            "विस्तृत बिल: आइटम कैटलॉग से सामान, मात्रा और छूट चुनकर बिल बनाएं",
            "मिश्रित भुगतान: कुछ नकद, कुछ UPI और बाकी ग्राहक के खाते में उधार दर्ज करें",
            "स्वचालित स्टॉक: बिल बनते ही गोदाम/दुकान से सामान अपने आप कम हो जाता है"
        ),
        proTipEn = "Cancelling a bill automatically restores deducted stock quantities back into your inventory.",
        proTipHi = "यदि कोई बिल रद्द किया जाता है, तो सामान का स्टॉक अपने आप वापस जुड़ जाता है।"
    ),
    WalkthroughStep(
        stepNumber = 5,
        icon = Icons.Default.LocalShipping,
        tagEn = "DISTRIBUTOR PAYABLES",
        tagHi = "थोक सप्लायर हिसाब",
        titleEn = "Wholesale Suppliers & Payables",
        titleHi = "थोक सप्लायर और देनदारी",
        subtitleEn = "Keep money owed to wholesale distributors strictly separated from customer receivables.",
        subtitleHi = "थोक व्यापारियों और डिस्ट्रीब्यूटरों का बकाया ग्राहकों की उधारी से बिल्कुल अलग रखें।",
        keyPointsEn = listOf(
            "Maintain an isolated directory of suppliers and distributors",
            "Record purchases on credit or cash and log settlement payments",
            "View full statement history and outstanding payables at a glance",
            "Supplier balances never contaminate customer udhaar or retail revenue"
        ),
        keyPointsHi = listOf(
            "थोक सप्लायरों और डिस्ट्रीब्यूटरों की अलग सूची बनाएं",
            "उधार या नकद माल खरीद दर्ज करें और भुगतान का हिसाब रखें",
            "सप्लायर का पूरा खाता विवरण और कुल देनदारी एक नज़र में देखें",
            "सप्लायर का हिसाब कभी भी ग्राहकों की उधारी या बिक्री में नहीं मिलता"
        ),
        proTipEn = "Record supplier purchases as soon as the delivery arrives to keep your purchase records up to date.",
        proTipHi = "माल दुकान में आते ही सप्लायर बिल दर्ज कर लें ताकि देनदारी का सही पता रहे।"
    ),
    WalkthroughStep(
        stepNumber = 6,
        icon = Icons.Default.AccountBalanceWallet,
        tagEn = "DRAWER AUDIT",
        tagHi = "रोकड़ सुरक्षा",
        titleEn = "Daily Cash Drawer Closing (Rokad)",
        titleHi = "दैनिक रोकड़ मिलान (दुकान बंद करते समय)",
        subtitleEn = "Reconcile physical register cash every evening to catch discrepancies and prevent loss.",
        subtitleHi = "हर शाम गल्ले की नकद राशि का मिलान करें ताकि हिसाब में अंतर या भूल का तुरंत पता चल सके।",
        keyPointsEn = listOf(
            "Enter morning Opening Cash (Float) at start of business day",
            "Automated formula: Expected Cash = Opening + (Cash Sales + Udhaar Cash) - Cash Expenses",
            "Count drawer notes/coins and enter Counted Cash",
            "Instant difference badge indicates Balanced (संतुलित), Surplus (अतिरिक्त), or Shortage (कमी)"
        ),
        keyPointsHi = listOf(
            "सुबह दुकान खोलते ही गल्ले का आरंभिक नकद (Opening Cash) दर्ज करें",
            "सटीक सूत्र: अपेक्षित नकद = आरंभिक नकद + (नकद बिक्री + जमा उधारी) - नकद खर्च",
            "शाम को गल्ले के नोट/सिक्के गिनकर वास्तविक नकद दर्ज करें",
            "ऐप तुरंत बताता है कि गल्ले का हिसाब सही है, नकद कम है या ज़्यादा"
        ),
        proTipEn = "UPI payments go straight to your bank account, so they are kept separate from physical drawer cash.",
        proTipHi = "UPI का पैसा सीधे बैंक खाते में जाता है, इसलिए वह गल्ले के भौतिक नकद में नहीं जोड़ा जाता।"
    ),
    WalkthroughStep(
        stepNumber = 7,
        icon = Icons.Default.Security,
        tagEn = "SECURITY & BACKUPS",
        tagHi = "सुरक्षा व बैकअप",
        titleEn = "App Lock, Bluetooth Printing & Backups",
        titleHi = "ऐप लॉक, थर्मल प्रिंटर और सुरक्षित बैकअप",
        subtitleEn = "Protect your shop data with military-grade AES-256-GCM backups and PIN security.",
        subtitleHi = "4-अंकों के पिन, फिंगरप्रिंट और AES-256-GCM एन्क्रिप्टेड बैकअप से दुकान का पूरा हिसाब सुरक्षित रखें।",
        keyPointsEn = listOf(
            "Set 4-digit PIN or fingerprint unlock with brute-force delay protection",
            "Direct Bluetooth printing for standard 58mm and 80mm thermal receipt printers",
            "Export encrypted backups with your own private password to Google Drive or SD card",
            "Serverless 1-tap updates include automatic pre-update safety backups"
        ),
        keyPointsHi = listOf(
            "4-अंकों का पिन या फिंगरप्रिंट लॉक लगाएं ताकि कोई अन्य व्यक्ति हिसाब न देख सके",
            "58mm और 80mm ब्लूटूथ थर्मल प्रिंटर से ग्राहकों को तुरंत पक्की पर्ची दें",
            "पासवर्ड डालकर अपने डेटा का सुरक्षित बैकअप बनाएं और गूगल ड्राइव में रखें",
            "वन-टैप अपडेट से बिना डेटा खोए ऐप को सुरक्षित रूप से अपडेट करें"
        ),
        proTipEn = "Create an encrypted backup once a week and save it to Google Drive so you never lose your records.",
        proTipHi = "हफ़्ते में एक बार बैकअप बनाकर गूगल ड्राइव में सेव कर लें ताकि फोन खोने पर भी हिसाब सुरक्षित रहे।"
    )
)

@Composable
fun OnboardingWalkthroughScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var isHindi by remember { mutableStateOf(false) }

    val step = walkthroughSteps[currentStepIndex]
    val totalSteps = walkthroughSteps.size

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar: Step counter, Language toggle, and Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step Badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (isHindi) "कदम ${step.stepNumber} / $totalSteps" else "Step ${step.stepNumber} of $totalSteps",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Language Switch Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { isHindi = !isHindi }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHindi) "English" else "हिन्दी",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Skip Button
                    TextButton(onClick = onComplete) {
                        Text(
                            text = if (isHindi) "छोड़ें" else "Skip",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Progress Indicator Bar
            LinearProgressIndicator(
                progress = { (currentStepIndex + 1).toFloat() / totalSteps.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = KiranaGreenPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Main Content Area with Animated Transition
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "WalkthroughStepAnimation"
                ) { targetStep ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Large Hero Icon Container
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(KiranaGreenPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = targetStep.icon,
                                contentDescription = null,
                                tint = KiranaGreenPrimary,
                                modifier = Modifier.size(46.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Feature Tag
                        Text(
                            text = if (isHindi) targetStep.tagHi else targetStep.tagEn,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = KiranaGreenPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Step Title
                        Text(
                            text = if (isHindi) targetStep.titleHi else targetStep.titleEn,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Subtitle
                        Text(
                            text = if (isHindi) targetStep.subtitleHi else targetStep.subtitleEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Key Points Card
                        KiranaCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (isHindi) "मुख्य बिंदु और उपयोग के तरीके:" else "Key Features & How to Use:",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val points = if (isHindi) targetStep.keyPointsHi else targetStep.keyPointsEn
                            points.forEach { point ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = KiranaCreditGreen,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = point,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Pro Tip Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (isHindi) "दुकानदार के लिए खास सुझाव:" else "Shopkeeper Pro Tip:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isHindi) targetStep.proTipHi else targetStep.proTipEn,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            // Bottom Navigation Controls
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = { currentStepIndex-- },
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHindi) "पीछे" else "Back",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(80.dp))
                    }

                    // Indicator Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(totalSteps) { idx ->
                            val isSelected = idx == currentStepIndex
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(if (isSelected) 22.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) KiranaGreenPrimary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            )
                        }
                    }

                    // Next or Finish Button
                    if (currentStepIndex < totalSteps - 1) {
                        Button(
                            onClick = { currentStepIndex++ },
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KiranaGreenPrimary)
                        ) {
                            Text(
                                text = if (isHindi) "आगे" else "Next",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Button(
                            onClick = onComplete,
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KiranaCreditGreen)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHindi) "दुकान शुरू करें" else "Get Started",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
