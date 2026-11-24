package com.example.data.catalog

import com.example.data.model.BodyStyle
import com.example.data.model.Manufacturer
import com.example.data.model.VehicleModel
import com.example.data.model.VehicleVariant

/**
 * Clearly labeled Demonstration Vehicle Catalog.
 * Never presented as OEM-verified specifications; used solely to demonstrate
 * dependent Manufacturer -> Model -> Trim/Variant selection until a verified
 * vehicle specification catalog API is connected.
 */
object DemoVehicleCatalog {
    const val IS_DEMONSTRATION_CATALOG = true
    const val CATALOG_NOTICE = "Demonstration Catalog — Pre-filled specs are unverified examples."

    val manufacturers: List<Manufacturer> = listOf(
        Manufacturer("volvo", "Volvo", "Sweden"),
        Manufacturer("porsche", "Porsche", "Germany"),
        Manufacturer("bmw", "BMW", "Germany"),
        Manufacturer("toyota", "Toyota", "Japan"),
        Manufacturer("hyundai", "Hyundai", "South Korea"),
        Manufacturer("rivian", "Rivian", "United States"),
        Manufacturer("polestar", "Polestar", "Sweden")
    )

    val models: List<VehicleModel> = listOf(
        VehicleModel("volvo_ex30", "volvo", "EX30", BodyStyle.CROSSOVER, 2024, 2026),
        VehicleModel("volvo_xc60", "volvo", "XC60", BodyStyle.SUV, 2018, 2026),
        VehicleModel("volvo_v60", "volvo", "V60", BodyStyle.WAGON, 2019, 2026),
        VehicleModel("porsche_taycan", "porsche", "Taycan", BodyStyle.SEDAN, 2020, 2026),
        VehicleModel("porsche_911", "porsche", "911", BodyStyle.COUPE, 2015, 2026),
        VehicleModel("bmw_i4", "bmw", "i4", BodyStyle.SEDAN, 2022, 2026),
        VehicleModel("bmw_m3", "bmw", "M3", BodyStyle.SEDAN, 2021, 2026),
        VehicleModel("toyota_rav4", "toyota", "RAV4", BodyStyle.SUV, 2019, 2026),
        VehicleModel("toyota_gr_corolla", "toyota", "GR Corolla", BodyStyle.HATCHBACK, 2023, 2026),
        VehicleModel("hyundai_ioniq5", "hyundai", "IONIQ 5", BodyStyle.CROSSOVER, 2022, 2026),
        VehicleModel("rivian_r1t", "rivian", "R1T", BodyStyle.TRUCK, 2022, 2026),
        VehicleModel("polestar_2", "polestar", "Polestar 2", BodyStyle.SEDAN, 2021, 2026)
    )

    val variants: List<VehicleVariant> = listOf(
        // Volvo EX30
        VehicleVariant(
            id = "ex30_single_er",
            modelId = "volvo_ex30",
            trimName = "Single Motor Extended Range",
            engineType = "Rear Permanent-Magnet Electric (BEV)",
            engineDisplacement = "N/A (Electric)",
            transmission = "Single-Speed Direct Drive",
            fuelType = "Electric",
            suggestedMarkets = listOf("Sweden / EU", "United States", "United Kingdom")
        ),
        VehicleVariant(
            id = "ex30_twin_perf",
            modelId = "volvo_ex30",
            trimName = "Twin Motor Performance",
            engineType = "Dual-Motor AWD Electric (BEV)",
            engineDisplacement = "N/A (Electric)",
            transmission = "Single-Speed Direct Drive",
            fuelType = "Electric",
            suggestedMarkets = listOf("Sweden / EU", "United States", "United Kingdom")
        ),
        // Volvo XC60
        VehicleVariant(
            id = "xc60_b5",
            modelId = "volvo_xc60",
            trimName = "B5 AWD Core",
            engineType = "2.0L Turbo Inline-4 Mild Hybrid",
            engineDisplacement = "1969 cc",
            transmission = "8-Speed Geartronic Automatic",
            fuelType = "Hybrid Petrol-Electric",
            suggestedMarkets = listOf("United States", "Sweden / EU", "Canada")
        ),
        VehicleVariant(
            id = "xc60_t8",
            modelId = "volvo_xc60",
            trimName = "T8 Recharge Polestar Engineered",
            engineType = "2.0L Turbo + Rear Electric (PHEV)",
            engineDisplacement = "1969 cc",
            transmission = "8-Speed Automatic AWD",
            fuelType = "Plug-in Hybrid",
            suggestedMarkets = listOf("Sweden / EU", "United States", "Germany")
        ),
        // Volvo V60
        VehicleVariant(
            id = "v60_cc",
            modelId = "volvo_v60",
            trimName = "Cross Country B5 AWD",
            engineType = "2.0L Turbo Inline-4 Mild Hybrid",
            engineDisplacement = "1969 cc",
            transmission = "8-Speed Automatic",
            fuelType = "Hybrid Petrol-Electric",
            suggestedMarkets = listOf("Sweden / EU", "United States", "Germany")
        ),
        // Porsche Taycan
        VehicleVariant(
            id = "taycan_4s",
            modelId = "porsche_taycan",
            trimName = "4S",
            engineType = "Dual-Motor AWD Electric (BEV)",
            engineDisplacement = "N/A (Electric)",
            transmission = "2-Speed Rear / Direct Front",
            fuelType = "Electric",
            suggestedMarkets = listOf("Germany", "United States", "United Kingdom")
        ),
        // Porsche 911
        VehicleVariant(
            id = "911_carrera_gts",
            modelId = "porsche_911",
            trimName = "Carrera GTS",
            engineType = "Twin-Turbo Flat-6",
            engineDisplacement = "2981 cc",
            transmission = "8-Speed PDK Dual-Clutch",
            fuelType = "Petrol / Gasoline",
            suggestedMarkets = listOf("Germany", "United States", "Japan")
        ),
        // BMW i4
        VehicleVariant(
            id = "i4_edrive40",
            modelId = "bmw_i4",
            trimName = "eDrive40 Gran Coupe",
            engineType = "Rear Electrically Excited Synchronous (BEV)",
            engineDisplacement = "N/A (Electric)",
            transmission = "Single-Speed Direct Drive",
            fuelType = "Electric",
            suggestedMarkets = listOf("Germany", "United States", "United Kingdom")
        ),
        // BMW M3
        VehicleVariant(
            id = "m3_comp",
            modelId = "bmw_m3",
            trimName = "Competition xDrive",
            engineType = "Twin-Turbo Inline-6 (S58)",
            engineDisplacement = "2993 cc",
            transmission = "8-Speed M Steptronic Automatic",
            fuelType = "Petrol / Gasoline",
            suggestedMarkets = listOf("Germany", "United States", "Canada")
        ),
        // Toyota RAV4
        VehicleVariant(
            id = "rav4_hybrid_xle",
            modelId = "toyota_rav4",
            trimName = "Hybrid XLE AWD",
            engineType = "2.5L Inline-4 Dynamic Force Hybrid",
            engineDisplacement = "2487 cc",
            transmission = "eCVT Electronic Continuous",
            fuelType = "Hybrid Petrol-Electric",
            suggestedMarkets = listOf("United States", "Japan", "Australia")
        ),
        // Toyota GR Corolla
        VehicleVariant(
            id = "gr_corolla_core",
            modelId = "toyota_gr_corolla",
            trimName = "Circuit Edition",
            engineType = "1.6L Turbo Inline-3 (G16E-GTS)",
            engineDisplacement = "1618 cc",
            transmission = "6-Speed Intelligent Manual",
            fuelType = "Petrol / Gasoline",
            suggestedMarkets = listOf("Japan", "United States", "Australia")
        ),
        // Hyundai IONIQ 5
        VehicleVariant(
            id = "ioniq5_awd",
            modelId = "hyundai_ioniq5",
            trimName = "Limited AWD",
            engineType = "Dual-Motor 800V Electric (BEV)",
            engineDisplacement = "N/A (Electric)",
            transmission = "Single-Speed Direct Drive",
            fuelType = "Electric",
            suggestedMarkets = listOf("South Korea", "United States", "Germany")
        ),
        // Rivian R1T
        VehicleVariant(
            id = "r1t_dual_max",
            modelId = "rivian_r1t",
            trimName = "Dual-Motor Max Pack",
            engineType = "Dual-Motor AWD Electric (BEV)",
            engineDisplacement = "N/A (Electric)",
            transmission = "Single-Speed Direct Drive",
            fuelType = "Electric",
            suggestedMarkets = listOf("United States", "Canada")
        ),
        // Polestar 2
        VehicleVariant(
            id = "p2_lr_dm",
            modelId = "polestar_2",
            trimName = "Long Range Dual Motor",
            engineType = "Dual-Motor AWD Electric (BEV)",
            engineDisplacement = "N/A (Electric)",
            transmission = "Single-Speed Direct Drive",
            fuelType = "Electric",
            suggestedMarkets = listOf("Sweden / EU", "United States", "United Kingdom")
        )
    )

    val commonEngineTypes = listOf(
        "Dual-Motor Electric (BEV)",
        "Single-Motor Electric (BEV)",
        "Hybrid (HEV)",
        "Plug-in Hybrid (PHEV)",
        "Inline-4 Turbo Petrol",
        "Inline-4 Naturally Aspirated Petrol",
        "Inline-6 / V6 Petrol",
        "V8 Petrol",
        "Inline-4 Diesel",
        "Unknown / Unavailable"
    )

    val commonTransmissions = listOf(
        "Single-Speed Direct Drive",
        "8-Speed Automatic",
        "7-Speed Dual-Clutch (DCT / PDK)",
        "6-Speed Manual",
        "eCVT / CVT",
        "10-Speed Automatic",
        "Unknown / Unavailable"
    )

    val commonFuelTypes = listOf(
        "Electric",
        "Petrol / Gasoline",
        "Hybrid Petrol-Electric",
        "Plug-in Hybrid",
        "Diesel",
        "Hydrogen (FCEV)",
        "Unknown / Unavailable"
    )

    val internationalMarkets = listOf(
        "United States",
        "Sweden / EU",
        "Germany",
        "United Kingdom",
        "Japan",
        "South Korea",
        "Canada",
        "Australia",
        "Switzerland",
        "Norway",
        "United Arab Emirates",
        "Other / Global"
    )

    fun modelsForManufacturer(manufacturerName: String): List<VehicleModel> {
        val mfr = manufacturers.firstOrNull { it.name.equals(manufacturerName.trim(), ignoreCase = true) }
            ?: return emptyList()
        return models.filter { it.manufacturerId == mfr.id }
    }

    fun variantsForModel(manufacturerName: String, modelName: String): List<VehicleVariant> {
        val mfrModels = modelsForManufacturer(manufacturerName)
        val model = mfrModels.firstOrNull { it.name.equals(modelName.trim(), ignoreCase = true) }
            ?: return emptyList()
        return variants.filter { it.modelId == model.id }
    }
}
