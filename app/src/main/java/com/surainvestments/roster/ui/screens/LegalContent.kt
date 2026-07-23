package com.surainvestments.roster.ui.screens

/** Ported verbatim from the iOS app's `TermsOfServiceContent` / `PrivacyPolicyContent`. */
data class LegalSection(
    val heading: String,
    val paragraphs: List<String>,
)

object TermsOfServiceContent {
    const val EFFECTIVE_DATE = "16 July 2026"

    val intro = """
        These Terms of Service ("Terms") govern your access to and use of Rosterra ("the App"), which is owned and operated by SURA INVESTMENTS PTY LTD ("Company", "we", "our", or "us").

        By creating an account, accessing, or using Rosterra, you agree to be bound by these Terms. If you do not agree to these Terms, you must not use the App.
    """.trimIndent()

    val sections: List<LegalSection> = listOf(
        LegalSection("1. About Rosterra", listOf(
            "Rosterra is a workforce scheduling and roster management platform designed to help businesses create, manage, and share employee rosters, communicate with team members, manage shift changes, and perform related workforce management functions.",
            "We may update, improve, modify, or discontinue features of the App at any time.",
        )),
        LegalSection("2. Eligibility", listOf(
            "You must be at least 18 years of age, or the age of legal majority in your jurisdiction, to create an account.",
            "If you register or use the App on behalf of a business or other organization, you represent and warrant that you have the authority to bind that organization to these Terms.",
        )),
        LegalSection("3. User Accounts", listOf(
            "You agree to:",
            "• Provide accurate, complete, and current registration information.\n• Maintain the security and confidentiality of your login credentials.\n• Notify us promptly of any unauthorized access to your account.\n• Accept responsibility for all activities conducted through your account.",
            "You are responsible for ensuring your account information remains accurate.",
        )),
        LegalSection("4. Acceptable Use", listOf(
            "You agree not to:",
            "• Use the App for any unlawful or fraudulent purpose.\n• Access another user's account without authorization.\n• Upload malware, viruses, or malicious code.\n• Interfere with the operation, security, or integrity of the App.\n• Attempt to reverse engineer, decompile, or copy any part of the App except where permitted by law.\n• Misuse the App in any way that could harm other users or the Company.",
            "We reserve the right to suspend or terminate accounts that violate these Terms.",
        )),
        LegalSection("5. Employer and Employee Responsibilities", listOf(
            "Businesses using Rosterra are responsible for:",
            "• Maintaining accurate employee information.\n• Creating and publishing accurate work schedules.\n• Complying with all applicable employment, workplace, and payroll laws.\n• Managing user permissions appropriately.",
            "Employees and team members are responsible for reviewing their assigned schedules and keeping their contact details current.",
        )),
        LegalSection("6. User Content", listOf(
            "You retain ownership of any schedules, messages, documents, files, or other content you submit to the App.",
            "By uploading content, you grant SURA INVESTMENTS PTY LTD a non-exclusive, worldwide, royalty-free licence to host, process, store, display, and transmit that content solely for the purpose of operating, maintaining, securing, and improving the App.",
            "You represent that you have all necessary rights to upload and share your content.",
        )),
        LegalSection("7. Privacy", listOf(
            "Our collection, use, and disclosure of personal information are governed by our Privacy Policy.",
            "By using Rosterra, you acknowledge that your personal information will be handled in accordance with our Privacy Policy.",
        )),
        LegalSection("8. Subscription and Payments", listOf(
            "Where paid subscriptions are offered:",
            "• Subscription fees are charged in advance.\n• Subscription fees are non-refundable except where required by applicable law.\n• Prices may change with reasonable notice.\n• Failure to pay applicable fees may result in suspension or termination of access.\n• Applicable taxes may be charged where required.",
        )),
        LegalSection("9. Intellectual Property", listOf(
            "Rosterra, including its software, source code, design, branding, logos, graphics, text, documentation, and other content, is owned by SURA INVESTMENTS PTY LTD or its licensors and is protected by applicable intellectual property laws.",
            "No ownership rights are transferred to users through use of the App.",
        )),
        LegalSection("10. Availability of Service", listOf(
            "While we aim to provide reliable service, we do not guarantee uninterrupted or error-free operation.",
            "Maintenance, upgrades, technical issues, or events beyond our reasonable control may temporarily affect availability.",
        )),
        LegalSection("11. Third-Party Services", listOf(
            "Rosterra may integrate with third-party services such as payroll providers, calendar services, messaging platforms, authentication providers, or cloud storage providers.",
            "We are not responsible for the availability, functionality, or policies of third-party services.",
        )),
        LegalSection("12. Disclaimer", listOf(
            "To the maximum extent permitted by law, Rosterra is provided on an \"as is\" and \"as available\" basis.",
            "SURA INVESTMENTS PTY LTD makes no warranties or guarantees that:",
            "• The App will always be available.\n• The App will operate without interruption or errors.\n• Information stored in the App will never be lost.\n• The App will meet every user's specific requirements.",
            "Nothing in these Terms excludes consumer guarantees that cannot legally be excluded under the Australian Consumer Law.",
        )),
        LegalSection("13. Limitation of Liability", listOf(
            "To the fullest extent permitted by law, SURA INVESTMENTS PTY LTD will not be liable for any indirect, incidental, special, consequential, or punitive damages arising out of or relating to the use of Rosterra.",
            "Where liability cannot be excluded, our liability is limited to the maximum extent permitted by applicable law.",
            "If permitted by law, our total liability for any claim will not exceed the greater of:",
            "• The amount paid by you for the App during the twelve (12) months immediately preceding the claim; or\n• AUD \$100.",
        )),
        LegalSection("14. Indemnity", listOf(
            "You agree to indemnify and hold harmless SURA INVESTMENTS PTY LTD, its directors, officers, employees, contractors, and affiliates from any claims, liabilities, damages, costs, or expenses arising from:",
            "• Your use of the App.\n• Your breach of these Terms.\n• Your violation of applicable laws or the rights of another person.",
        )),
        LegalSection("15. Suspension and Termination", listOf(
            "We may suspend or terminate your account immediately if:",
            "• You breach these Terms.\n• You engage in unlawful activity.\n• Your use poses a security risk.\n• You misuse or abuse the App.",
            "Accounts are employer-managed. Staff may request account deletion in-app; a manager reviews and may approve, after which access is locked and sign-in is removed after a grace period as described in our Privacy Policy. Managers may delete staff accounts in the App. Manager / business-owner accounts are not self-deletable in this version; organisation closure will be handled by Super Admin when Rosterra becomes multi-tenant SaaS.",
            "You may stop using Rosterra at any time.",
        )),
        LegalSection("16. Data Retention", listOf(
            "We may retain account information and associated records for legal, regulatory, security, operational, and backup purposes in accordance with our Privacy Policy and applicable law.",
            "Where required for Australian tax, payroll, and employment record-keeping, identity and payroll-related records (including name, date of birth, address, Tax File Number, timesheets, and payslips) may be retained after sign-in access is removed.",
        )),
        LegalSection("17. Changes to These Terms", listOf(
            "We may amend these Terms from time to time.",
            "Where changes are material, we will provide reasonable notice through the App or by email. Continued use of Rosterra after the updated Terms take effect constitutes acceptance of the revised Terms.",
        )),
        LegalSection("18. Governing Law", listOf(
            "These Terms are governed by the laws of the State of South Australia and the laws of the Commonwealth of Australia.",
            "Any dispute arising from these Terms shall be subject to the exclusive jurisdiction of the courts of South Australia, unless applicable law provides otherwise.",
        )),
        LegalSection("19. Contact Us", listOf(
            "SURA INVESTMENTS PTY LTD\nEmail: support@sura-roster.com\nBusiness Address: 66 Wellington Road, Mount Barker, SA, 5251.\nWebsite: https://sura-roster.com/home",
        )),
        LegalSection("20. Entire Agreement", listOf(
            "These Terms constitute the entire agreement between you and SURA INVESTMENTS PTY LTD regarding your use of Rosterra and supersede all prior agreements relating to the App.",
        )),
        LegalSection("21. Severability", listOf(
            "If any provision of these Terms is held to be invalid, illegal, or unenforceable, the remaining provisions will continue in full force and effect.",
        )),
        LegalSection("22. Waiver", listOf(
            "Our failure to enforce any right or provision under these Terms does not constitute a waiver of that right or provision.",
        )),
    )
}

object PrivacyPolicyContent {
    const val LAST_UPDATED = "16 July 2026"

    val intro = """
        This Privacy Policy explains how SURA INVESTMENTS PTY LTD ("we", "our", or "us") collects, uses, stores, and protects personal information when you use the Rosterra application for Android, and our related website.

        By using Rosterra, you acknowledge that your personal information will be handled in accordance with this Privacy Policy.
    """.trimIndent()

    val sections: List<LegalSection> = listOf(
        LegalSection("Information We Collect", listOf(
            "Depending on how you use Rosterra, we may collect the following information:",
        )),
        LegalSection("Account and Profile Information", listOf(
            "Your employer may provide information including:",
            "• Full name\n• Email address\n• Phone number (where provided)\n• Employment role\n• Workplace or team assignment",
            "This information is used to create and manage your account.",
        )),
        LegalSection("Tax and Employment Identity", listOf(
            "Your manager may store employment identity details for payroll and Australian tax (ATO) record-keeping, including:",
            "• Date of birth\n• Address\n• Tax File Number (TFN)\n• Employee identifiers",
            "TFN is manager-only and is not shown to staff in the app. Payslips may store the last four digits of your TFN as a snapshot at generation time.",
        )),
        LegalSection("Roster and Employment Data", listOf(
            "We collect information required to provide workforce management features, including:",
            "• Work schedules and shifts\n• Availability\n• Leave requests\n• Shift changes and approvals\n• Timesheet entries\n• Attendance records\n• Payslip information made available by your employer\n• Wage assignment history",
        )),
        LegalSection("Location Information", listOf(
            "When you start or end a shift, the app may record your device location at that moment to verify attendance at your workplace.",
            "Location is captured only around clock-in and clock-out using while-in-use permission. It is not tracked continuously in the background.",
        )),
        LegalSection("Photos", listOf(
            "If you choose to add a profile picture, or attach a task or reference photo, the image you select (from the camera or your photo library) is uploaded and stored with your employer's workspace.",
            "Camera access is used to take photos for those features; photos taken in-app for tasks are not automatically saved to your device photo gallery.",
        )),
        LegalSection("Device Information and Diagnostics", listOf(
            "To maintain the security and reliability of the App, we may collect limited technical information, including:",
            "• Device type\n• Operating system version\n• App version\n• Push notification token\n• Crash and diagnostic information via Firebase Crashlytics",
            "This information is used to diagnose issues and keep notifications working. It is not used for advertising.",
        )),
        LegalSection("Usage Analytics", listOf(
            "The Rosterra Android app does not use Firebase Analytics or advertising identifiers.",
            "The signed-in web version of Rosterra may use Firebase Analytics to collect limited usage events (such as sign-in activity, timesheet submissions, and feature usage) to improve performance and resolve issues. Analytics data is not used for advertising purposes.",
            "Our public marketing website does not use cookies, advertising technologies, or visitor analytics.",
        )),
        LegalSection("How We Use Your Information", listOf(
            "We use personal information to:",
            "• Provide roster, scheduling, timesheet, leave, attendance, and payslip functionality.\n• Verify shift attendance using location captured at clock-in and clock-out.\n• Manage your account and workplace permissions.\n• Send notifications about roster updates, approvals, reminders, and other work-related events.\n• Respond to support requests.\n• Diagnose crashes and improve reliability.\n• Protect the security and integrity of our services.\n• Comply with legal and regulatory obligations, including payroll and tax record-keeping.",
            "We do not sell your personal information.",
        )),
        LegalSection("Sharing Your Information", listOf(
            "We only share personal information where necessary to operate the service.",
            "This may include:",
            "• Your employer, who controls your workplace data.\n• Google Firebase and Google Cloud Platform, which provide secure hosting, authentication, storage, crash diagnostics, and notification services (and, for the signed-in web app, limited analytics).\n• Service providers who assist in operating the App under appropriate confidentiality and security obligations.\n• Government authorities where required by law.",
            "We do not share your personal information with third parties for marketing purposes.",
        )),
        LegalSection("Data Storage and Security", listOf(
            "Rosterra stores data using Google Firebase hosted on Google Cloud Platform.",
            "We use appropriate technical and organisational measures to protect personal information, including:",
            "• Secure authentication.\n• Encrypted network communications (HTTPS/TLS).\n• Access controls restricting data to authorised users within your employer's workspace.\n• Secure storage of authentication credentials using the Android Keystore.\n• Optional biometric authentication (such as fingerprint or face unlock) on supported Android devices.",
            "Although we take reasonable steps to protect your information, no method of electronic transmission or storage is completely secure.",
        )),
        LegalSection("Account Deletion and Data Retention", listOf(
            "Accounts are employer-managed.",
            "Staff may request deletion in-app (Account → Delete account). A manager reviews the request. On approval, the account is locked immediately so you cannot sign in. For 30 days the manager may cancel the deletion and reinstate access. After 30 days, Firebase Auth login and push notification tokens are permanently removed.",
            "Managers (business owners) may initiate staff account deletion from the Staff tools in the app. Manager / business-owner accounts cannot be self-deleted in this version for safety. Organisation or owner-account closure will be handled by a Super Admin console when Rosterra becomes multi-tenant SaaS.",
            "We retain personal information only for as long as necessary to provide the service, meet contractual obligations with your employer, and comply with payroll, taxation, employment, and legal record-keeping requirements.",
            "When an account's sign-in is removed, identity and payroll records your employer needs for Australian tax and employment obligations — including name, date of birth, address, TFN, timesheets, shifts, attendance records, payslips, and related wage history — are retained for as long as required by law and your employer's record-keeping policy. They are not wiped solely because login access is removed.",
            "When information is no longer required, it is securely deleted or de-identified where reasonably practicable.",
        )),
        LegalSection("Your Rights", listOf(
            "Depending on applicable privacy laws, you may have the right to:",
            "• Request access to your personal information.\n• Request correction of inaccurate or incomplete information.\n• Request deletion of your personal information where permitted by law.\n• Request information about how your personal information is processed.",
            "As most workplace information is managed on behalf of your employer, some requests may need to be handled through your employer.",
            "To make a privacy request, contact:\nEmail: support@sura-roster.com",
        )),
        LegalSection("Children's Privacy", listOf(
            "Rosterra is designed for workplace use and is not intended for children under the age of 16.",
            "We do not knowingly collect personal information directly from children.",
        )),
        LegalSection("Cookies and Tracking", listOf(
            "Our public website does not use cookies, advertising technologies, or visitor analytics.",
            "Rosterra does not use advertising identifiers or third-party advertising trackers.",
            "The Android app uses Firebase Crashlytics for crash diagnostics only. The signed-in web app may use Firebase Analytics as described above.",
        )),
        LegalSection("International Data Transfers", listOf(
            "Because Rosterra uses Google Cloud and Firebase services, your information may be processed or stored on servers located outside Australia. Where this occurs, we take reasonable steps to ensure your personal information receives appropriate protection consistent with applicable privacy laws.",
        )),
        LegalSection("Changes to This Privacy Policy", listOf(
            "We may update this Privacy Policy from time to time.",
            "When significant changes are made, we will update the \"Last updated\" date and may notify users through the App or by other appropriate means.",
            "Your continued use of Rosterra after changes take effect constitutes acceptance of the updated Privacy Policy.",
        )),
        LegalSection("Contact Us", listOf(
            "If you have questions about this Privacy Policy or our privacy practices, please contact:\n\nSURA INVESTMENTS PTY LTD\nAdelaide, South Australia\nEmail: support@sura-roster.com\nBusiness Address: 66 Wellington Road, Mount Barker, SA, 5251.\nWebsite: https://sura-roster.com/home",
        )),
    )
}
