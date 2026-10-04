package app.family.ledger.data

/**
 * Initial example data from the family's description. Everything here is editable in the app.
 * No rents, phone numbers or household counts are invented: those are left empty for the user to fill in.
 */
object Seed {
    val UNIT_TYPES = listOf("Apartment", "House", "Room", "Shop", "Office", "Storage", "Other")
    val EXPENSE_CATEGORIES = listOf("Repair", "Maintenance", "Electricity", "Water", "Materials", "Contractor", "Security", "Other")
    val DEBT_TYPES = listOf("Old Rent Debt", "Current Rent Arrears", "Repair Charge", "Utility Charge", "Other")

    suspend fun runIfNeeded(dao: AppDao) {
        if (dao.setting("seeded") != null) return
        if (dao.buildingCount() > 0) { dao.upSetting(Setting("seeded", "1")); return }

        UNIT_TYPES.forEach { dao.upOption(OptionItem(kind = "UNIT_TYPE", value = it)) }
        EXPENSE_CATEGORIES.forEach { dao.upOption(OptionItem(kind = "EXPENSE_CATEGORY", value = it)) }
        DEBT_TYPES.forEach { dao.upOption(OptionItem(kind = "DEBT_TYPE", value = it)) }

        val muhammad = Person(name = "محمد")
        val omar = Person(name = "عمر")
        dao.upPerson(muhammad); dao.upPerson(omar)

        suspend fun building(n: String, name: String, order: Int) =
            Building(number = n, name = name, sortOrder = order).also { dao.upBuilding(it) }

        suspend fun floor(b: Building, num: Int, name: String, ownership: String = "Ours") =
            Floor(buildingId = b.id, number = num, name = name, ownership = ownership, sortOrder = num).also { dao.upFloor(it) }

        var unitCounter = 0
        suspend fun unit(
            b: Building, f: Floor, name: String, type: String, status: String,
            ownership: String = "Ours", collectedFor: String? = null, tenantName: String? = null
        ) {
            unitCounter++
            val u = PropertyUnit(
                buildingId = b.id, floorId = f.id, number = unitCounter.toString(), name = name, type = type,
                ownership = ownership, status = status, defaultCollectedForId = collectedFor
            )
            dao.upUnit(u)
            if (status == "Rented") {
                dao.upTenant(Tenant(unitId = u.id, name = tenantName ?: "مستأجر (الاسم لاحقاً)"))
            }
        }

        // Building 1
        val b1 = building("1", "مبنى 1", 1)
        val b1g = floor(b1, 0, "الطابق الأرضي"); val b1f = floor(b1, 1, "الطابق الأول"); val b1s = floor(b1, 2, "الطابق الثاني")
        unit(b1, b1g, "محل الوالد", "Shop", "Family", "Family")
        unit(b1, b1g, "بيت مؤجر", "House", "Rented")
        unit(b1, b1f, "بيت العم", "House", "Family", "Family")
        unit(b1, b1s, "بيت ابن العم", "House", "Family", "Family")

        // Building 2: main building
        val b2 = building("2", "المبنى الرئيسي", 2)
        val b2g = floor(b2, 0, "الطابق الأرضي"); val b2f = floor(b2, 1, "الطابق الأول")
        val b2s = floor(b2, 2, "الطابق الثاني"); val b2t = floor(b2, 3, "الطابق الثالث", "Not ours")
        unit(b2, b2g, "بيت الجدة", "House", "Family", "Family")
        unit(b2, b2g, "البيت قبل بيت الجدة", "House", "Rented")
        unit(b2, b2f, "بيت العائلة", "House", "Family", "Family")
        unit(b2, b2f, "بيت الوالد", "House", "Family", "Family")
        unit(b2, b2f, "بيت مؤجر", "House", "Rented")
        unit(b2, b2s, "شمال", "Room", "Rented")
        unit(b2, b2s, "وسط", "Room", "Rented")
        unit(b2, b2s, "يمين", "Room", "Rented")
        unit(b2, b2s, "قرب الدرج", "Room", "Rented")
        unit(b2, b2t, "الطابق الثالث", "Other", "Not ours", "Not ours")

        // Building 3: next to the garden
        val b3 = building("3", "المبنى المجاور للحديقة", 3)
        val b3g = floor(b3, 0, "الطابق الأرضي"); val b3f = floor(b3, 1, "الطابق الأول"); val b3s = floor(b3, 2, "الطابق الثاني")
        unit(b3, b3g, "وحدة مؤجرة", "Apartment", "Rented", tenantName = "البنغالي")
        unit(b3, b3f, "وحدة مؤجرة", "Apartment", "Rented", tenantName = "أبو هاني")
        unit(b3, b3s, "وحدة مؤجرة", "Apartment", "Rented")

        // Building 4: two houses, rent collected for other people (default only; each payment stores its own choice)
        val b4 = building("4", "مبنى 4", 4)
        val b4g = floor(b4, 0, "الطابق الأرضي"); floor(b4, 1, "السطح")
        unit(b4, b4g, "بيت 1", "House", "Rented", collectedFor = muhammad.id)
        unit(b4, b4g, "بيت 2", "House", "Rented", collectedFor = omar.id)

        dao.upSetting(Setting("lang", "ar"))
        dao.upSetting(Setting("theme", "light"))
        dao.upSetting(Setting("default_currency", "USD"))
        dao.upSetting(Setting("default_due_day", "1"))
        dao.upSetting(Setting("seeded", "1"))
    }
}
