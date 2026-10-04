package app.family.ledger.ui

/** English UI key -> Arabic. Missing keys fall back to English. Names and notes typed by the user are never translated. */
val ARABIC: Map<String, String> = mapOf(
    // navigation / titles
    "Home" to "الرئيسية", "Rentals" to "الإيجارات", "Rent" to "الإيجار", "For Others" to "لآخرين", "More" to "المزيد",
    "My Rentals" to "إيجاراتي", "Collected for Others" to "محصّل لآخرين", "Tenants" to "المستأجرون", "Tenant" to "المستأجر",
    "Debts" to "الديون", "Maintenance" to "الصيانة", "Expenses" to "المصاريف", "Reports" to "التقارير", "Buildings" to "المباني",
    "Floor Plan" to "مخطط الطوابق", "Settings" to "الإعدادات", "Search" to "بحث", "Building" to "المبنى", "Floor" to "الطابق",
    "Floors" to "الطوابق", "Units" to "الوحدات", "Unit" to "الوحدة", "Photos" to "الصور", "Payments" to "الدفعات",
    "People" to "الأشخاص", "Residents" to "الساكنون",
    // dashboard
    "Rented units" to "وحدات مؤجرة", "People living in them" to "عدد الساكنين", "Vacant" to "شاغرة", "Rent this month" to "إيجار هذا الشهر",
    "Collected" to "المحصّل", "Not paid yet" to "غير مدفوع بعد", "Overdue rent" to "إيجار متأخر", "Old debts" to "ديون قديمة",
    "Other charges" to "رسوم أخرى", "Expenses this month" to "مصاريف هذا الشهر", "Net income this month" to "صافي الدخل هذا الشهر",
    "To hand over" to "للتسليم", "Open maintenance" to "صيانة مفتوحة", "Quick Actions" to "إجراءات سريعة",
    "Add Rent Payment" to "إضافة دفعة إيجار", "Add Debt" to "إضافة دين", "Add expense" to "إضافة مصروف", "Add issue" to "إضافة مشكلة",
    "Record Handover" to "تسجيل تسليم", "Add unit" to "إضافة وحدة", "Add rental unit" to "إضافة وحدة للإيجار",
    "Money collected for other people is not counted as family income." to "المال المحصّل لأشخاص آخرين لا يُحسب من دخل العائلة.",
    // statuses
    "Paid" to "مدفوع", "Partially paid" to "مدفوع جزئياً", "Unpaid" to "غير مدفوع", "Overdue" to "متأخر",
    "Rented" to "مؤجرة", "Family" to "عائلة", "Not ours" to "ليست لنا", "Open" to "مفتوحة", "In Progress" to "قيد التنفيذ",
    "Completed" to "مكتملة", "Cancelled" to "ملغاة", "All" to "الكل",
    // rent
    "Add payment" to "إضافة دفعة", "Left" to "المتبقي", "of" to "من", "for" to "لأجل", "Rent due" to "الإيجار المستحق",
    "Monthly" to "شهرياً", "Monthly rent" to "الإيجار الشهري", "Rent not set" to "لم يُحدَّد الإيجار", "Balance" to "الرصيد",
    "No rent entries for this month." to "لا توجد إيجارات لهذا الشهر.",
    "Some tenants have no rent amount yet. Open the tenant and set the monthly rent." to "بعض المستأجرين بلا مبلغ إيجار. افتح المستأجر وحدّد الإيجار الشهري.",
    "Rent months" to "أشهر الإيجار", "Rent unpaid" to "إيجار غير مدفوع", "Debts and charges" to "الديون والرسوم", "Total" to "المجموع",
    "Due day" to "يوم الاستحقاق", "Beginning of month" to "بداية الشهر", "End of month" to "نهاية الشهر", "Custom day" to "يوم محدد",
    "Rental start date" to "تاريخ بدء الإيجار", "End date" to "تاريخ الانتهاء",
    // payment dialog
    "Amount" to "المبلغ", "Date" to "التاريخ", "Notes" to "ملاحظات", "Apply to" to "تسديد على",
    "Oldest balance first (automatic)" to "الأقدم أولاً (تلقائي)", "Collected For" to "محصّل لأجل", "No one" to "لا أحد",
    "Collected for someone else is saved on this payment only. It never changes past or future payments." to "اختيار «محصّل لأجل» يُحفظ على هذه الدفعة فقط ولا يغيّر الدفعات السابقة أو القادمة.",
    "Credit not applied" to "رصيد غير مستخدم", "No payments yet" to "لا توجد دفعات بعد", "Voided" to "ملغاة",
    "Void this payment" to "إلغاء هذه الدفعة", "Void" to "إلغاء",
    "The payment stays in the history marked as voided, and the balance is restored." to "تبقى الدفعة في السجل كملغاة، ويعود الرصيد كما كان.",
    // debts
    "No debts. Everything is paid." to "لا توجد ديون. كل شيء مدفوع.", "Former tenant" to "مستأجر سابق", "Type" to "النوع",
    "Description" to "الوصف", "Old Rent Debt" to "دين إيجار قديم", "Current Rent Arrears" to "متأخرات الإيجار الحالية",
    "Repair Charge" to "رسوم إصلاح", "Utility Charge" to "رسوم خدمات", "Old debts" to "ديون قديمة",
    // others
    "Handed over" to "المسلّم", "Remaining" to "المتبقي", "Person" to "الشخص", "Add person" to "إضافة شخص", "Edit person" to "تعديل شخص",
    "Hide history" to "إخفاء السجل", "Show history" to "عرض السجل", "Archived" to "مؤرشف",
    // maintenance
    "Title" to "العنوان", "Estimated cost" to "التكلفة المقدّرة", "Actual cost" to "التكلفة الفعلية", "Nothing here." to "لا يوجد شيء هنا.",
    "Photos of the problem" to "صور المشكلة", "Edit" to "تعديل", "Status" to "الحالة",
    // expenses / reports
    "Category" to "الفئة", "Edit expense" to "تعديل مصروف", "This month" to "هذا الشهر", "Last month" to "الشهر الماضي",
    "This year" to "هذه السنة", "Last year" to "السنة الماضية", "All time" to "كل الفترات", "Custom range" to "فترة مخصصة",
    "From" to "من", "To" to "إلى", "Net income" to "صافي الدخل", "Added in this period" to "أُضيف في هذه الفترة",
    "Paid against debts" to "المدفوع من الديون", "Collected for others (not income)" to "محصّل لآخرين (ليس دخلاً)",
    "Own rent collected minus own expenses." to "الإيجار المحصّل لنا ناقص مصاريفنا.",
    "Repair" to "إصلاح", "Electricity" to "كهرباء", "Water" to "مياه", "Materials" to "مواد", "Contractor" to "مقاول",
    "Security" to "الأمان", "Other" to "أخرى",
    "January" to "يناير", "February" to "فبراير", "March" to "مارس", "April" to "أبريل", "May" to "مايو", "June" to "يونيو",
    "July" to "يوليو", "August" to "أغسطس", "September" to "سبتمبر", "October" to "أكتوبر", "November" to "نوفمبر", "December" to "ديسمبر",
    // buildings
    "Add Building" to "إضافة مبنى", "Edit building" to "تعديل مبنى", "Building number" to "رقم المبنى", "Address / location" to "العنوان / الموقع",
    "Add floor" to "إضافة طابق", "Edit floor" to "تعديل طابق", "Floor number" to "رقم الطابق", "Ownership" to "الملكية",
    "Ours" to "لنا", "Managed for another" to "ندير لغيرنا", "Shared" to "مشتركة",
    "Buildings, floors and all units, including family units. Everything can be edited." to "المباني والطوابق وكل الوحدات بما فيها بيوت العائلة. كل شيء قابل للتعديل.",
    "No units on this floor." to "لا توجد وحدات في هذا الطابق.", "Colors always come with a word and an icon." to "الألوان تأتي دائماً مع كلمة وأيقونة.",
    "Edit unit" to "تعديل وحدة", "Unit name" to "اسم الوحدة", "Unit number" to "رقم الوحدة", "Add new" to "إضافة جديد",
    "Show family and other units" to "عرض بيوت العائلة والوحدات الأخرى", "Rent is usually collected for" to "الإيجار يُحصَّل عادةً لأجل",
    "Apartment" to "شقة", "House" to "بيت", "Room" to "غرفة", "Shop" to "محل", "Office" to "مكتب", "Storage" to "مخزن",
    // tenants
    "Add tenant" to "إضافة مستأجر", "Edit tenant" to "تعديل مستأجر", "Full name" to "الاسم الكامل", "Name" to "الاسم", "Phone" to "الهاتف",
    "Phone 2" to "هاتف ثانٍ", "Email" to "البريد الإلكتروني", "Who lives here" to "من يسكن هنا", "Men" to "شباب", "Women" to "نساء",
    "Individuals" to "أفراد", "Father" to "أب", "Mother" to "أم", "Sons" to "أبناء", "Daughters" to "بنات", "Total people" to "عدد الأشخاص",
    "Responsible person" to "الشخص المسؤول", "Relationship" to "صلة القرابة", "Add resident" to "إضافة ساكن", "Age" to "العمر",
    "Male" to "ذكر", "Female" to "أنثى", "Tenancy history" to "سجل الإيجارات السابقة",
    "End tenancy" to "إنهاء الإيجار", "End tenancy / replace tenant" to "إنهاء الإيجار / تغيير المستأجر",
    "The tenant stays in the history with all payments. You can then add a new tenant." to "يبقى المستأجر في السجل مع كل دفعاته. بعدها يمكنك إضافة مستأجر جديد.",
    "Include former tenants" to "إظهار المستأجرين السابقين", "Room photos" to "صور الغرفة",
    // photos
    "Gallery" to "المعرض", "Camera" to "الكاميرا", "No photos yet" to "لا توجد صور بعد", "Delete photo" to "حذف الصورة", "Close" to "إغلاق",
    // settings
    "Language" to "اللغة", "Night mode" to "الوضع الليلي", "Light" to "نهاري", "Dark" to "ليلي", "Default currency" to "العملة الافتراضية",
    "Default rent due day" to "يوم استحقاق الإيجار الافتراضي", "Backup" to "النسخ الاحتياطي",
    "Your data lives only on this phone. Nothing is sent anywhere unless you share a backup yourself." to "بياناتك موجودة على هذا الهاتف فقط. لا يُرسل شيء لأي مكان إلا إذا شاركت نسخة احتياطية بنفسك.",
    "Share Data Only" to "مشاركة البيانات فقط", "Share Data + Photos" to "مشاركة البيانات + الصور", "Restore Data" to "استعادة البيانات",
    "Export CSV (Excel)" to "تصدير CSV (إكسل)", "PIN lock" to "قفل بالرقم السري", "On" to "مفعّل", "Off" to "متوقف",
    "Set a PIN" to "تعيين رقم سري", "Change or remove PIN" to "تغيير أو إزالة الرقم السري", "Remove PIN" to "إزالة الرقم السري",
    "New PIN (4-8 digits)" to "رقم سري جديد (4-8 أرقام)", "The PIN is kept on this phone only." to "الرقم السري محفوظ على هذا الهاتف فقط.",
    "Enter PIN" to "أدخل الرقم السري", "Wrong PIN" to "الرقم السري خاطئ", "Unlock" to "فتح",
    "Archive" to "الأرشيف", "Restore" to "استرجاع", "Nothing archived." to "لا يوجد شيء في الأرشيف.",
    "Archived items keep all their history. Archive an item from its Edit screen." to "العناصر المؤرشفة تحتفظ بكل سجلها. للأرشفة افتح شاشة التعديل.",
    "Data statistics" to "إحصائيات البيانات", "Database version" to "إصدار قاعدة البيانات", "Internet permission" to "إذن الإنترنت",
    "None" to "لا يوجد", "About" to "حول", "Private family property ledger. Works fully offline." to "دفتر عقارات عائلي خاص. يعمل بالكامل بدون إنترنت.",
    "Backup date" to "تاريخ النسخة", "Financial records" to "السجلات المالية", "files in backup" to "ملفات في النسخة", "Replace data" to "استبدال البيانات",
    "This will replace the current local data with the backup. A safety copy of the current data is saved first. Do you want to continue?" to "سيتم استبدال البيانات الحالية بالنسخة الاحتياطية. تُحفظ أولاً نسخة أمان من البيانات الحالية. هل تريد المتابعة؟",
    "Restore finished" to "تمت الاستعادة", "Something went wrong" to "حدث خطأ", "This is not a valid backup file" to "هذا الملف ليس نسخة احتياطية صالحة",
    "Receipt photo" to "صورة الإيصال", "Save the expense first, then open it to add a receipt photo." to "احفظ المصروف أولاً ثم افتحه لإضافة صورة الإيصال.",
    // generic
    "Save" to "حفظ", "Cancel" to "إلغاء", "OK" to "موافق", "Show less" to "عرض أقل", "Show all" to "عرض الكل",
    "Search tenant, phone, unit…" to "بحث عن مستأجر أو رقم أو وحدة…"
)
