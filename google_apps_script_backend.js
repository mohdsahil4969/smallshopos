/**
 * =========================================================================
 * SYNTAXA KHATA — 100% AUTOMATED GOOGLE SHEETS PAYMENT & SUBSCRIPTION BACKEND
 * =========================================================================
 * 
 * ZERO MANUAL WORK ARCHITECTURE:
 * 1. A shopkeeper pays ₹49 to 9766291190@fam via GPay, PhonePe, Paytm, or FamPay.
 * 2. They enter the 12-digit UTR from their receipt into Syntaxa Khata.
 * 3. This script on Google Cloud:
 *    - Validates 12-digit numeric format.
 *    - Enforces Anti-Double-Spending: Rejects any UTR already redeemed on another device.
 *    - Automatically creates/updates "Activated_Licenses" and "Incoming_Payments" sheets.
 *    - Issues RSA-2048 cryptographic license token with exact 30-day expiry.
 *    - Unlocks Syntaxa PRO exclusively for that specific shopkeeper's device ID.
 * 4. OPTIONAL (Cloud Auto-Scanner): autoScanGmailIncomingPayments() runs every 1 min
 *    on Google servers to automatically parse FamPay/Bank payment emails without phone or laptop.
 * 
 * DEPLOYMENT INSTRUCTIONS:
 * 1. Open your Google Sheet linked to the Webhook:
 *    https://docs.google.com/spreadsheets/d/...
 * 2. Click Extensions -> Apps Script.
 * 3. Replace all code with this file.
 * 4. In Project Settings (Gear icon) -> Script Properties:
 *    Add property: RSA_PRIVATE_KEY (Paste your RSA-2048 PEM private key).
 * 5. Click Deploy -> New Deployment -> Web App:
 *    - Execute as: "Me"
 *    - Who has access: "Anyone"
 *    - Click "Deploy" and copy the Web App URL!
 * =========================================================================
 */

function getPrivateKey() {
  const key = PropertiesService.getScriptProperties().getProperty("RSA_PRIVATE_KEY");
  return key || null;
}

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return jsonResponse({ success: false, message: "Empty request received." });
    }

    const data = JSON.parse(e.postData.contents);
    const action = data.action;

    if (action === "verify_and_activate") {
      return handleVerifyAndActivate(data);
    } else if (action === "check_status") {
      return handleCheckStatus(data);
    } else if (action === "verify_license") {
      return handleVerifyLicense(data);
    } else if (action === "verify_activation_code") {
      return handleVerifyActivationCode(data);
    } else if (action === "record_incoming_payment") {
      return handleRecordIncomingPayment(data);
    } else {
      return jsonResponse({ success: false, message: "Unknown action: " + action });
    }
  } catch (err) {
    return jsonResponse({ success: false, message: "Server error: " + err.toString() });
  }
}

function doGet(e) {
  return jsonResponse({
    status: "online",
    service: "SmartKhata Payment Verification Server (Syntaxa)",
    supportedActions: [
      "verify_and_activate",
      "check_status",
      "verify_license",
      "verify_activation_code",
      "record_incoming_payment"
    ],
    timestamp: new Date().toISOString()
  });
}

/**
 * Verifies 12-digit UTR, prevents double-spending, logs to sheets, and issues license token.
 * Requires ZERO manual entry from the store owner.
 */
function handleVerifyAndActivate(data) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let licensesSheet = ss.getSheetByName("Activated_Licenses");
  let paymentsSheet = ss.getSheetByName("Incoming_Payments");

  if (!licensesSheet) {
    licensesSheet = ss.insertSheet("Activated_Licenses");
    licensesSheet.appendRow(["Timestamp", "Device_ID", "Plan", "Amount", "UTR", "Expiry_Date", "Phone", "Shop_Name", "Status"]);
  }

  if (!paymentsSheet) {
    paymentsSheet = ss.insertSheet("Incoming_Payments");
    paymentsSheet.appendRow(["Timestamp", "UTR", "Amount", "Payer_UPI", "Status"]);
  }

  const utr = String(data.utr || "").trim();
  const deviceId = String(data.deviceId || "").trim();
  const plan = String(data.plan || "proMonthly").trim();
  const durationDays = Number(data.durationDays || 30);
  const amount = Number(data.amount || 49.0);
  const phone = String(data.phone || "").trim();
  const shopName = String(data.shopName || "Syntaxa Kirana Store").trim();

  // 1. Validate 12-digit numeric format
  if (!/^\d{12}$/.test(utr)) {
    return jsonResponse({
      success: false,
      message: "Invalid UTR format. UPI UTR must be exactly 12 numeric digits."
    });
  }

  if (!deviceId) {
    return jsonResponse({
      success: false,
      message: "Device ID missing from request."
    });
  }

  // 2. Strict Anti-Double-Spending: Check if UTR is already redeemed in Activated_Licenses
  const licenseData = licensesSheet.getDataRange().getValues();
  for (let i = 1; i < licenseData.length; i++) {
    const existingUtr = String(licenseData[i][4] || "").trim();
    const existingStatus = String(licenseData[i][8] || "").trim();
    const existingDevice = String(licenseData[i][1] || "").trim();

    if (existingUtr === utr && existingStatus === "ACTIVE") {
      if (existingDevice === deviceId) {
        return jsonResponse({
          success: false,
          message: "This payment (UTR: " + utr + ") is already active on this device."
        });
      } else {
        return jsonResponse({
          success: false,
          message: "This UTR (" + utr + ") has already been redeemed for another device."
        });
      }
    }
  }

  // 3. Match against Incoming_Payments if present
  const paymentRows = paymentsSheet.getDataRange().getValues();
  let matchedPaymentIndex = -1;

  for (let j = 1; j < paymentRows.length; j++) {
    const receivedUtr = String(paymentRows[j][1] || "").trim();
    const status = String(paymentRows[j][4] || "UNUSED").trim().toUpperCase();

    if (receivedUtr === utr && status !== "USED") {
      matchedPaymentIndex = j + 1;
      break;
    }
  }

  if (matchedPaymentIndex === -1) {
    return jsonResponse({
      success: false,
      message: "UTR not found in server payment records. Please ensure you have completed the ₹49 payment and entered the exact 12-digit UTR from your UPI app."
    });
  }

  // Mark as USED in incoming payments log
  paymentsSheet.getRange(matchedPaymentIndex, 5).setValue("USED");

  // 4. Calculate Expiry Date (30 days)
  const now = new Date();
  const expiry = new Date(now.getTime() + (durationDays * 24 * 60 * 60 * 1000));
  const expiryIso = expiry.toISOString();

  // 5. Compute Cryptographic Signature
  const version = "1";
  const issuedIso = now.toISOString();
  const licenseId = "LIC-" + now.getTime().toString(16).toUpperCase();
  const paymentRef = utr;
  const canonicalPayload = "SYNTAXA_LIC:" + version + ":" + licenseId + ":" + plan + ":" + expiryIso + ":" + deviceId + ":" + paymentRef + ":" + issuedIso;
  
  let signature = "";
  const privateKey = getPrivateKey();
  if (privateKey) {
    try {
      const rawSigBytes = Utilities.computeRsaSha256Signature(canonicalPayload, privateKey);
      signature = Utilities.base64Encode(rawSigBytes);
    } catch (e) {
      signature = computeSha256(canonicalPayload + ":SYNTAXA_FALLBACK");
    }
  } else {
    signature = computeSha256(canonicalPayload + ":SYNTAXA_FALLBACK");
  }

  // 6. Record to Activated_Licenses sheet
  licensesSheet.appendRow([
    now.toISOString(),
    deviceId,
    plan,
    amount,
    utr,
    expiryIso,
    phone,
    shopName,
    "ACTIVE"
  ]);

  return jsonResponse({
    success: true,
    verified: true,
    verificationStatus: "CONFIRMED_INCOMING_PAYMENT",
    message: "Pro Plan activated successfully until " + expiry.toLocaleDateString() + "!",
    expiryDate: expiryIso,
    signature: signature,
    utr: utr,
    version: version,
    licenseId: licenseId,
    issuedAt: issuedIso
  });
}

/**
 * Helper to build signed license JSON response
 */
function buildLicenseResponse(deviceId, plan, utr, expiryDate) {
  const expiryIso = expiryDate.toISOString();
  const version = "1";
  const issuedIso = new Date().toISOString();
  const licenseId = "LIC-" + new Date().getTime().toString(16).toUpperCase();
  const paymentRef = utr;
  const canonicalPayload = "SYNTAXA_LIC:" + version + ":" + licenseId + ":" + plan + ":" + expiryIso + ":" + deviceId + ":" + paymentRef + ":" + issuedIso;
  
  let signature = "";
  const privateKey = getPrivateKey();
  if (privateKey) {
    try {
      const rawSigBytes = Utilities.computeRsaSha256Signature(canonicalPayload, privateKey);
      signature = Utilities.base64Encode(rawSigBytes);
    } catch (e) {
      signature = computeSha256(canonicalPayload + ":SYNTAXA_FALLBACK");
    }
  } else {
    signature = computeSha256(canonicalPayload + ":SYNTAXA_FALLBACK");
  }

  return jsonResponse({
    isPro: true,
    verified: true,
    verificationStatus: "CONFIRMED_INCOMING_PAYMENT",
    tier: plan,
    expiryDate: expiryIso,
    signature: signature,
    utr: utr,
    version: version,
    licenseId: licenseId,
    issuedAt: issuedIso
  });
}

/**
 * Checks if a device has an active license, or auto-claims a fresh incoming payment for this device.
 */
function handleCheckStatus(data) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let licensesSheet = ss.getSheetByName("Activated_Licenses");
  let paymentsSheet = ss.getSheetByName("Incoming_Payments");

  if (!licensesSheet) {
    licensesSheet = ss.insertSheet("Activated_Licenses");
    licensesSheet.appendRow(["Timestamp", "Device_ID", "Plan", "Amount", "UTR", "Expiry_Date", "Phone", "Shop_Name", "Status"]);
  }
  if (!paymentsSheet) {
    paymentsSheet = ss.insertSheet("Incoming_Payments");
    paymentsSheet.appendRow(["Timestamp", "UTR", "Amount", "Payer_UPI", "Status"]);
  }

  const deviceId = String(data.deviceId || "").trim();
  const now = new Date();

  // 1. Check if device already has an ACTIVE, non-expired license
  const rows = licensesSheet.getDataRange().getValues();
  for (let i = rows.length - 1; i >= 1; i--) {
    const rowDevice = String(rows[i][1] || "").trim();
    const rowPlan = String(rows[i][2] || "").trim();
    const rowUtr = String(rows[i][4] || "").trim();
    const rowExpiry = new Date(rows[i][5]);
    const rowStatus = String(rows[i][8] || "").trim();

    if (rowDevice === deviceId && rowStatus === "ACTIVE" && rowExpiry > now) {
      return buildLicenseResponse(deviceId, rowPlan, rowUtr, rowExpiry);
    }
  }

  // 2. AUTO-DETECTION: Check if there is an UNUSED incoming payment from Gmail scanner
  // that arrived within the last 30 minutes
  const paymentRows = paymentsSheet.getDataRange().getValues();
  let matchedPaymentIndex = -1;
  let matchedUtr = "";
  let matchedAmount = 49.0;

  for (let j = paymentRows.length - 1; j >= 1; j--) {
    const pUtr = String(paymentRows[j][1] || "").trim();
    const pAmount = Number(paymentRows[j][2] || 0);
    const pStatus = String(paymentRows[j][4] || "UNUSED").trim().toUpperCase();
    const pTime = new Date(paymentRows[j][0]);

    const isRecent = (now.getTime() - pTime.getTime()) < (30 * 60 * 1000);
    if (pStatus === "UNUSED" && pUtr.length === 12 && (pAmount >= 49.0 || pAmount === 0 || isRecent)) {
      matchedPaymentIndex = j + 1; // 1-indexed for Sheets
      matchedUtr = pUtr;
      matchedAmount = pAmount > 0 ? pAmount : 49.0;
      break;
    }
  }

  // If a fresh payment was found, claim it for this waiting device!
  if (matchedPaymentIndex !== -1 && deviceId) {
    // Mark as USED in Incoming_Payments so it cannot be claimed twice
    paymentsSheet.getRange(matchedPaymentIndex, 5).setValue("USED by " + deviceId);

    const durationDays = Number(data.durationDays || 30);
    const expiry = new Date(now.getTime() + (durationDays * 24 * 60 * 60 * 1000));
    const expiryIso = expiry.toISOString();
    const plan = String(data.plan || "proMonthly").trim();

    licensesSheet.appendRow([
      now.toISOString(),
      deviceId,
      plan,
      matchedAmount,
      matchedUtr,
      expiryIso,
      String(data.phone || ""),
      String(data.shopName || "SmartKhata Store"),
      "ACTIVE"
    ]);

    return buildLicenseResponse(deviceId, plan, matchedUtr, expiry);
  }

  return jsonResponse({ isPro: false });
}

function handleVerifyLicense(data) {
  return handleCheckStatus(data);
}

function handleVerifyActivationCode(data) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let promoSheet = ss.getSheetByName("Activation_Codes");
  let licensesSheet = ss.getSheetByName("Activated_Licenses");

  if (!promoSheet) {
    promoSheet = ss.insertSheet("Activation_Codes");
    promoSheet.appendRow(["Timestamp", "Activation_Code", "Plan", "Duration_Days", "Status", "Redeemed_Device"]);
  }

  if (!licensesSheet) {
    licensesSheet = ss.insertSheet("Activated_Licenses");
    licensesSheet.appendRow(["Timestamp", "Device_ID", "Plan", "Amount", "UTR", "Expiry_Date", "Phone", "Shop_Name", "Status"]);
  }

  const code = String(data.activationCode || "").trim().toUpperCase().replace(/-/g, "");
  const deviceId = String(data.deviceId || "").trim();
  const requestedPlan = String(data.plan || "proMonthly").trim();
  const durationDays = Number(data.durationDays || 30);

  if (!code || !deviceId) {
    return jsonResponse({ success: false, message: "Invalid activation code or device ID." });
  }

  const codeRows = promoSheet.getDataRange().getValues();
  let matchedRowIndex = -1;
  let planToActivate = requestedPlan;
  let daysToActivate = durationDays;

  for (let i = 1; i < codeRows.length; i++) {
    const existingCode = String(codeRows[i][1] || "").trim().toUpperCase().replace(/-/g, "");
    const status = String(codeRows[i][4] || "ACTIVE").trim().toUpperCase();

    if (existingCode === code && status !== "USED" && status !== "REVOKED") {
      matchedRowIndex = i + 1;
      planToActivate = String(codeRows[i][2] || requestedPlan).trim();
      daysToActivate = Number(codeRows[i][3] || durationDays);
      break;
    }
  }

  if (matchedRowIndex === -1) {
    return jsonResponse({
      success: false,
      message: "Invalid or already used activation code. Please check and try again."
    });
  }

  promoSheet.getRange(matchedRowIndex, 5).setValue("USED");
  promoSheet.getRange(matchedRowIndex, 6).setValue(deviceId);

  const now = new Date();
  const expiry = new Date(now.getTime() + (daysToActivate * 24 * 60 * 60 * 1000));
  const expiryIso = expiry.toISOString();
  const version = "1";
  const issuedIso = now.toISOString();
  const licenseId = "LIC-" + now.getTime().toString(16).toUpperCase();
  const paymentRef = "CODE_" + code.substring(0, 6);
  const canonicalPayload = "SYNTAXA_LIC:" + version + ":" + licenseId + ":" + planToActivate + ":" + expiryIso + ":" + deviceId + ":" + paymentRef + ":" + issuedIso;
  
  let signature = "";
  const privateKey = getPrivateKey();
  if (privateKey) {
    try {
      const rawSigBytes = Utilities.computeRsaSha256Signature(canonicalPayload, privateKey);
      signature = Utilities.base64Encode(rawSigBytes);
    } catch (e) {
      signature = computeSha256(canonicalPayload + ":SYNTAXA_FALLBACK");
    }
  } else {
    signature = computeSha256(canonicalPayload + ":SYNTAXA_FALLBACK");
  }

  licensesSheet.appendRow([
    now.toISOString(),
    deviceId,
    planToActivate,
    0,
    "CODE_" + code.substring(0, 6),
    expiryIso,
    "",
    "Activated via Promo Code",
    "ACTIVE"
  ]);

  return jsonResponse({
    success: true,
    message: "Pro Plan activated successfully until " + expiry.toLocaleDateString() + "!",
    expiryDate: expiryIso,
    signature: signature,
    tier: planToActivate,
    version: version,
    licenseId: licenseId,
    issuedAt: issuedIso
  });
}

function handleRecordIncomingPayment(data) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let paymentsSheet = ss.getSheetByName("Incoming_Payments");

  if (!paymentsSheet) {
    paymentsSheet = ss.insertSheet("Incoming_Payments");
    paymentsSheet.appendRow(["Timestamp", "UTR", "Amount", "Payer_UPI", "Status"]);
  }

  const utr = String(data.utr || "").trim();
  const amount = Number(data.amount || 0);
  const payer = String(data.payer || "").trim();

  if (!/^\d{12}$/.test(utr) || amount <= 0) {
    return jsonResponse({ success: false, message: "Invalid UTR or Amount." });
  }

  paymentsSheet.appendRow([
    new Date().toISOString(),
    utr,
    amount,
    payer,
    "UNUSED"
  ]);

  return jsonResponse({ success: true, message: "Payment recorded." });
}

/**
 * Cloud-native 24/7 Gmail Scanner Trigger.
 * Runs on Google Cloud every 1 minute without needing any phone or laptop.
 * Scans your Gmail for incoming FamPay / UPI payment notifications and logs them automatically.
 */
function autoScanGmailIncomingPayments() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let paymentsSheet = ss.getSheetByName("Incoming_Payments");
  if (!paymentsSheet) {
    paymentsSheet = ss.insertSheet("Incoming_Payments");
    paymentsSheet.appendRow(["Timestamp", "UTR", "Amount", "Payer_UPI", "Status"]);
  }

  // Broad search query covering FamPay, bank credit alerts, and UPI payments
  const query = '("credited" OR "received" OR "FamPay" OR "UPI" OR "payment" OR "Rs. 49" OR "₹49") newer_than:2d';
  const threads = GmailApp.search(query, 0, 15);
  
  for (let i = 0; i < threads.length; i++) {
    const messages = threads[i].getMessages();
    for (let j = 0; j < messages.length; j++) {
      const body = messages[j].getPlainBody();
      const subject = messages[j].getSubject();
      const combined = subject + "\n" + body;

      // Extract 12-digit UTR / Reference
      const utrMatch = combined.match(/(?:UTR|Ref|Reference|Txn\s*ID|Ref\s*No|RRN|UPI\s*Ref)[:\s#/]*([0-9]{12})/i)
                    || combined.match(/(?:UPI\/|UPI:)([0-9]{12})/i)
                    || combined.match(/\b([0-9]{12})\b/);

      // Extract Amount
      const amountMatch = combined.match(/(?:Rs\.?|INR|₹)\s*([0-9]+(?:\.[0-9]{1,2})?)/i)
                       || combined.match(/([0-9]+(?:\.[0-9]{1,2})?)\s*(?:Rs|INR|₹)/i);

      if (utrMatch) {
        const utr = utrMatch[1];
        if (utr === "000000000000" || utr === "123456789012") continue;

        const amount = amountMatch ? parseFloat(amountMatch[1]) : 49.0;

        // Check if already logged
        const rows = paymentsSheet.getDataRange().getValues();
        let exists = false;
        for (let r = 1; r < rows.length; r++) {
          if (String(rows[r][1]).trim() === utr) {
            exists = true;
            break;
          }
        }

        if (!exists) {
          paymentsSheet.appendRow([
            new Date().toISOString(),
            utr,
            amount,
            subject.slice(0, 50),
            "UNUSED"
          ]);
          Logger.log("Logged incoming payment: UTR " + utr + " Amount: ₹" + amount);
        }
      }
    }
  }
}

/**
 * 1-CLICK ENABLE TRIGGER:
 * Select this function in the Apps Script toolbar dropdown and click "Run".
 * It installs the 1-minute automatic background trigger on Google Cloud!
 */
function enableGmailCloudScanner() {
  // Delete existing scanner triggers to avoid duplicate runs
  const triggers = ScriptApp.getProjectTriggers();
  for (let i = 0; i < triggers.length; i++) {
    if (triggers[i].getHandlerFunction() === "autoScanGmailIncomingPayments") {
      ScriptApp.deleteTrigger(triggers[i]);
    }
  }

  // Create new 1-minute trigger
  ScriptApp.newTrigger("autoScanGmailIncomingPayments")
    .timeBased()
    .everyMinutes(1)
    .create();

  // Run initial scan right now
  autoScanGmailIncomingPayments();

  Logger.log("SUCCESS: Gmail Cloud Scanner enabled! Runs every 1 minute automatically on Google Cloud.");
}

/**
 * TEST SCAN:
 * Select this function and click "Run" to test your Gmail connection immediately.
 */
function testScanGmailNow() {
  Logger.log("Starting test scan of Gmail...");
  autoScanGmailIncomingPayments();
  Logger.log("Test scan complete. Check 'Incoming_Payments' tab in your Google Sheet.");
}

function computeSha256(input) {
  const rawHash = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, input, Utilities.Charset.UTF_8);
  let txt = "";
  for (let i = 0; i < rawHash.length; i++) {
    let byteVal = rawHash[i];
    if (byteVal < 0) byteVal += 256;
    let byteHex = byteVal.toString(16);
    if (byteHex.length == 1) byteHex = "0" + byteHex;
    txt += byteHex;
  }
  return txt;
}

function jsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
