package com.extrive.vigilex.data.mock

data class Site(
    val id: String,
    val name: String,
    val subtitle: String = ""
)

data class Area(
    val id: String,
    val name: String
)

data class Task(
    val id: String,
    val name: String
)

enum class AssessmentStatus { COMPLETED, REVIEW_REQUIRED }

data class RecentAssessment(
    val id: String,
    val areaName: String,
    val siteName: String,
    val timestamp: String,
    val status: AssessmentStatus
)

object MockData {
    val sites = listOf(
        Site("s1", "Chennai Manufacturing Plant", "Tamil Nadu"),
        Site("s2", "Bangalore Assembly Unit", "Karnataka"),
        Site("s3", "Hyderabad Distribution Center", "Telangana"),
        Site("s4", "Pune Production Facility", "Maharashtra"),
        Site("s5", "Mumbai Logistics Hub", "Maharashtra")
    )

    val areas = listOf(
        Area("a1", "Assembly Line A"),
        Area("a2", "Packaging"),
        Area("a3", "Warehouse"),
        Area("a4", "Maintenance"),
        Area("a5", "Quality Control")
    )

    val tasks = listOf(
        Task("t1", "Manual Lifting"),
        Task("t2", "Material Loading"),
        Task("t3", "Box Packing"),
        Task("t4", "Component Assembly"),
        Task("t5", "Machine Operation")
    )

    val recentAssessments = listOf(
        RecentAssessment(
            id = "r1",
            areaName = "Assembly Line",
            siteName = "Chennai Manufacturing Plant",
            timestamp = "Today, 10:42 AM",
            status = AssessmentStatus.COMPLETED
        ),
        RecentAssessment(
            id = "r2",
            areaName = "Material Handling",
            siteName = "Bangalore Assembly Unit",
            timestamp = "Yesterday, 3:18 PM",
            status = AssessmentStatus.COMPLETED
        ),
        RecentAssessment(
            id = "r3",
            areaName = "Packing Station",
            siteName = "Chennai Manufacturing Plant",
            timestamp = "Yesterday, 11:05 AM",
            status = AssessmentStatus.REVIEW_REQUIRED
        )
    )
}
