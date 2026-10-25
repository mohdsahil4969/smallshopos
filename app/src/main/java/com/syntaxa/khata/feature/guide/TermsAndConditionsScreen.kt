package com.syntaxa.khata.feature.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.ui.components.KiranaCard
import com.syntaxa.khata.core.ui.theme.KiranaDebitRed
import com.syntaxa.khata.core.ui.theme.KiranaGreenPrimary

data class TermsSection(
    val id: String,
    val icon: ImageVector,
    val titleEn: String,
    val titleHi: String,
    val contentEn: String,
    val contentHi: String,
    val isCritical: Boolean = false
)

val termsSections = listOf(
    TermsSection(
        id = "machine_disclaimer",
        icon = Icons.Default.PrecisionManufacturing,
        titleEn = "Machine & Software Nature Disclaimer",
        titleHi = "मशीन एवं सॉफ्टवेयर की प्रकृति व अस्वीकरण",
        contentEn = "Syntaxa Khata is an automated electronic software program running on mobile hardware. Like any computer software, operating system, or machine, computational errors, operating system pauses, software bugs, storage failures, or unexpected device crashes can occur. The software is provided strictly on an \"AS IS\" and \"AS AVAILABLE\" basis without warranties of any kind.",
        contentHi = "सिंटैक्सा खाता एक स्वचालित इलेक्ट्रॉनिक सॉफ्टवेयर प्रोग्राम है जो मोबाइल हार्डवेयर पर चलता है। किसी भी कंप्यूटर सॉफ्टवेयर या मशीन की भांति इसमें सॉफ्टवेयर बग, सिस्टम रुकावट, गणना विसंगति, मेमोरी खराबी या अप्रत्याशित डिवाइस क्रैश हो सकता है। यह सॉफ्टवेयर पूर्णतः \"जैसी है वैसी\" (AS IS) स्थिति में बिना किसी वारंटी के प्रदान किया जाता है।",
        isCritical = true
    ),
    TermsSection(
        id = "liability_limitation",
        icon = Icons.Default.Gavel,
        titleEn = "Strict Limitation of Liability for Business Losses",
        titleHi = "व्यापारिक नुकसान के लिए शून्य देयता नीति",
        contentEn = "Under no circumstances shall Syntaxa Khata, its developers, authors, contributors, or distributors be held legally or financially liable for any direct, indirect, special, incidental, punitive, or consequential commercial or business losses, loss of profits, commercial disputes, customer misunderstandings, inventory shortages, tax penalties, or unrecovered credit/udhaar. Use of this application is at your sole discretion and risk.",
        contentHi = "इस एप्लिकेशन के उपयोग या किसी विसंगति के कारण होने वाले किसी भी प्रत्यक्ष, अप्रत्यक्ष, व्यावसायिक नुकसान, व्यापार घाटे, दुकान के मुनाफे में कमी, ग्राहकों से विवाद, सामान की कमी, कर संबंधी समस्याओं या न मिलने वाले उधार के लिए सिंटैक्सा खाता अथवा इसके डेवलपर्स किसी भी प्रकार से जिम्मेदार या उत्तरदायी नहीं होंगे। ऐप का उपयोग पूरी तरह आपके स्वयं के विवेक पर निर्भर है।",
        isCritical = true
    ),
    TermsSection(
        id = "shopkeeper_verification",
        icon = Icons.Default.VerifiedUser,
        titleEn = "Merchant Verification & Calculation Responsibility",
        titleHi = "दुकानदार का हिसाब जांचने का पूर्ण दायित्व",
        contentEn = "The merchant/shopkeeper holds 100% responsibility to double-check and independently verify all customer ledgers, daily sales, invoice totals, discount amounts, and cash drawer balances before handing over goods or concluding financial settlements with customers or suppliers.",
        contentHi = "ग्राहकों या सप्लायरों से लेन-देन या हिसाब चुकता करने से पहले सभी खाता लेजर, दैनिक बिक्री, बिल राशि, छूट और गल्ले के नकद मिलान की स्वयं जांच व सत्यापन करने की शत-प्रतिशत जिम्मेदारी स्वयं दुकानदार की है।",
        isCritical = false
    ),
    TermsSection(
        id = "local_privacy",
        icon = Icons.Default.Security,
        titleEn = "100% Offline Architecture & Data Privacy",
        titleHi = "100% ऑफ़लाइन प्रणाली व डेटा गोपनीयता",
        contentEn = "All accounting entries, customer telephone numbers, transaction histories, and inventory figures are saved exclusively on your local device storage. There are zero cloud servers tracking or storing your financial records. Your business data remains exclusively yours.",
        contentHi = "आपके सभी बहीखाता विवरण, ग्राहकों के नंबर, लेन-देन और स्टॉक की जानकारी केवल आपके फ़ोन की मेमोरी में सुरक्षित रहती है। किसी भी क्लाउड सर्वर पर आपकी कोई जानकारी नहीं भेजी जाती। आपकी दुकान का हिसाब केवल आपके पास है।",
        isCritical = false
    ),
    TermsSection(
        id = "backup_duty",
        icon = Icons.Default.CloudUpload,
        titleEn = "Mandatory Merchant Backup Duty",
        titleHi = "नियमित बैकअप लेने की अनिवार्य जिम्मेदारी",
        contentEn = "Because Syntaxa Khata does not upload your records to any remote server, it is the shopkeeper's sole responsibility to create periodic encrypted backups (via Settings -> Backup & Restore) and save them to Google Drive or external storage to prevent permanent data loss in case of phone theft, water damage, or hardware crash.",
        contentHi = "क्योंकि ऐप आपका डेटा किसी सर्वर पर स्टोर नहीं करता, इसलिए फ़ोन खोने, खराब होने या पानी में गिरने की स्थिति में डेटा के स्थायी नुकसान से बचने हेतु समय-समय पर पासवर्ड-सुरक्षित बैकअप (सेटिंग्स से) बनाकर सुरक्षित स्थान पर रखने की जिम्मेदारी दुकानदार की है।",
        isCritical = false
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAndConditionsScreen(
    onAccepted: () -> Unit,
    modifier: Modifier = Modifier,
    isReviewMode: Boolean = false,
    onBack: () -> Unit = {}
) {
    var hasAgreed by remember { mutableStateOf(isReviewMode) }
    var isHindi by remember { mutableStateOf(false) }

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
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReviewMode) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHindi) "शुरुआती सेटअप" else "Initial Setup",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Language toggle pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (!isHindi) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { isHindi = false }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "English",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (!isHindi) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (!isHindi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isHindi) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { isHindi = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "हिंदी",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isHindi) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isHindi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
            ) {
                // Title and Introduction
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isHindi) "नियम, शर्तें एवं कानूनी अस्वीकरण" else "Terms of Service & Disclaimer",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isHindi)
                                "सिंटैक्सा खाता का उपयोग करने से पूर्व कृपया इन आवश्यक परिचालन एवं कानूनी शर्तों को ध्यानपूर्वक पढ़ें।"
                            else
                                "Please carefully review these operational and legal terms before using Syntaxa Khata for your retail shop.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Prominent Notice Banner (Machine Error & Business Losses)
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.5.dp, KiranaDebitRed.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = KiranaDebitRed,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isHindi)
                                        "महत्वपूर्ण: मशीन त्रुटि व शून्य देयता अस्वीकरण"
                                    else
                                        "CRITICAL: Machine Disclaimer & Zero Liability",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (isHindi)
                                    "• सिंटैक्सा खाता एक कंप्यूटर/मोबाइल सॉफ्टवेयर है। मशीन और डिजिटल प्रोग्राम होने के कारण इसमें तकनीकी त्रुटि, बग्स या गणना विसंगति की सम्भावना हो सकती है।\n\n• इस ऐप के उपयोग से उत्पन्न किसी भी व्यापारिक नुकसान (Business Loss), मुनाफे में कमी या वित्तीय विवाद के लिए सिंटैक्सा खाता या इसके डेवलपर्स जिम्मेदार नहीं होंगे।\n\n• हिसाब-किताब के सही होने की अंतिम जांच करना पूर्णतः दुकानदार की अपनी जिम्मेदारी है।"
                                else
                                    "• Syntaxa Khata is a computer/mobile software tool. As with any software program or machine, technical bugs, unexpected errors, or calculation glitches may occur.\n\n• Neither Syntaxa Khata nor its creators/developers shall bear any liability for business losses, commercial disputes, lost profits, or financial discrepancies.\n\n• The shopkeeper is solely responsible for verifying the accuracy of all ledger balances and transactions.",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Section Cards
                items(termsSections, key = { it.id }) { section ->
                    KiranaCard {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (section.isCritical)
                                    MaterialTheme.colorScheme.errorContainer
                                else
                                    MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = section.icon,
                                        contentDescription = null,
                                        tint = if (section.isCritical)
                                            MaterialTheme.colorScheme.onErrorContainer
                                        else
                                            MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHindi) section.titleHi else section.titleEn,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isHindi) section.contentHi else section.contentEn,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Sticky Consent & Navigation Area
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (isReviewMode) {
                        Button(
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isHindi) "समझ गया (वापस जाएं)" else "Understood (Close)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    } else {
                        // Checkbox Agreement Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { hasAgreed = !hasAgreed }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = hasAgreed,
                                onCheckedChange = { hasAgreed = it },
                                colors = CheckboxDefaults.colors(checkedColor = KiranaGreenPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isHindi)
                                    "मैंने नियम व शर्तें, सॉफ्टवेयर-मशीन त्रुटि अस्वीकरण और देयता सीमा को ध्यानपूर्वक पढ़ व समझ लिया है और मैं इससे सहमत हूँ।"
                                else
                                    "I have read, understood, and accept the Terms of Service, Machine Error Disclaimer, and Limitation of Liability.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (hasAgreed) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Animated Next Button: Only shown when user ticks the checkbox!
                        AnimatedVisibility(
                            visible = hasAgreed,
                            enter = fadeIn() + slideInVertically { it / 2 },
                            exit = fadeOut() + slideOutVertically { it / 2 }
                        ) {
                            Button(
                                onClick = onAccepted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KiranaGreenPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = if (isHindi) "आगे बढ़ें (Next)" else "Next",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null
                                )
                            }
                        }

                        if (!hasAgreed) {
                            Text(
                                text = if (isHindi)
                                    "आगे बढ़ने के लिए कृपया ऊपर दिए गए चेकबॉक्स पर टिक करें"
                                else
                                    "Please check the box above to accept and proceed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
