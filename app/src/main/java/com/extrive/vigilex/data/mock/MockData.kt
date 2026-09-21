package com.extrive.vigilex.data.mock

data class Site(
    val id: String,
    val name: String,
    val location: String
)

data class Area(
    val id: String,
    val name: String,
    val detail: String
)

data class Task(
    val id: String,
    val name: String,
    val detail: String
)

enum class RiskLevel { LOW, MODERATE, HIGH }

enum class AssessmentStatus { COMPLETED, REVIEW_REQUIRED, DRAFT }

data class RecentAssessment(
    val id: String,
    val taskName: String,
    val areaName: String,
    val siteName: String,
    val timestamp: String,
    val riskLevel: RiskLevel,
    val status: AssessmentStatus,
    val score: Int
)

data class Report(
    val id: String,
    val title: String,
    val siteName: String,
    val generatedAt: String,
    val assessmentCount: Int,
    val overallRisk: RiskLevel
)

data class JointResult(
    val name: String,
    val level: RiskLevel,
    val detail: String
)

data class SummaryMetrics(
    val totalAssessments: Int,
    val moderateRisk: Int,
    val highRisk: Int,
    val reports: Int
)

object MockData {
    val sites = listOf(
        Site("s1", "Assembly Plant", "Chennai"),
        Site("s2", "Packaging Unit", "Madurai"),
        Site("s3", "Warehouse", "Coimbatore"),
        Site("s4", "Fabrication Shop", "Hosur"),
        Site("s5", "Distribution Center", "Bengaluru")
    )

    val recentSiteIds = listOf("s1", "s3")

    val areas = listOf(
        Area("a1", "Line 03", "Final assembly · 6 stations"),
        Area("a2", "Packaging Bay", "Cartoning and palletising"),
        Area("a3", "Inbound Dock", "Receiving and unloading"),
        Area("a4", "Maintenance Shop", "Tooling and repair"),
        Area("a5", "Quality Control", "Inspection benches")
    )

    val tasks = listOf(
        Task("t1", "Box Lifting", "Manual lifting from pallet to conveyor"),
        Task("t2", "Material Loading", "Loading components into fixtures"),
        Task("t3", "Overhead Fastening", "Torque work above shoulder height"),
        Task("t4", "Component Assembly", "Seated bench assembly"),
        Task("t5", "Machine Operation", "Standing press operation")
    )

    val metrics = SummaryMetrics(
        totalAssessments = 24,
        moderateRisk = 8,
        highRisk = 3,
        reports = 12
    )

    val assessments = listOf(
        RecentAssessment(
            id = "r1",
            taskName = "Box Lifting",
            areaName = "Line 03",
            siteName = "Assembly Plant",
            timestamp = "Today · 10:42 AM",
            riskLevel = RiskLevel.MODERATE,
            status = AssessmentStatus.COMPLETED,
            score = 72
        ),
        RecentAssessment(
            id = "r2",
            taskName = "Overhead Fastening",
            areaName = "Line 03",
            siteName = "Assembly Plant",
            timestamp = "Today · 9:15 AM",
            riskLevel = RiskLevel.HIGH,
            status = AssessmentStatus.REVIEW_REQUIRED,
            score = 86
        ),
        RecentAssessment(
            id = "r3",
            taskName = "Material Loading",
            areaName = "Inbound Dock",
            siteName = "Warehouse",
            timestamp = "Yesterday · 3:18 PM",
            riskLevel = RiskLevel.LOW,
            status = AssessmentStatus.COMPLETED,
            score = 34
        ),
        RecentAssessment(
            id = "r4",
            taskName = "Component Assembly",
            areaName = "Quality Control",
            siteName = "Assembly Plant",
            timestamp = "Yesterday · 11:05 AM",
            riskLevel = RiskLevel.MODERATE,
            status = AssessmentStatus.COMPLETED,
            score = 58
        ),
        RecentAssessment(
            id = "r5",
            taskName = "Machine Operation",
            areaName = "Packaging Bay",
            siteName = "Packaging Unit",
            timestamp = "Mon · 2:40 PM",
            riskLevel = RiskLevel.LOW,
            status = AssessmentStatus.DRAFT,
            score = 0
        )
    )

    val recentAssessments = assessments.take(3)

    val attentionAssessment = assessments.first { it.riskLevel == RiskLevel.HIGH }

    val reports = listOf(
        Report("p1", "Weekly ergonomic summary", "Assembly Plant", "Generated today", 9, RiskLevel.MODERATE),
        Report("p2", "Line 03 lifting review", "Assembly Plant", "Generated yesterday", 4, RiskLevel.HIGH),
        Report("p3", "Inbound dock assessment", "Warehouse", "Generated 3 days ago", 6, RiskLevel.LOW),
        Report("p4", "Packaging bay audit", "Packaging Unit", "Generated last week", 5, RiskLevel.MODERATE)
    )

    val jointResults = listOf(
        JointResult("Trunk", RiskLevel.MODERATE, "Flexion 38°"),
        JointResult("Neck", RiskLevel.LOW, "Flexion 12°"),
        JointResult("Shoulder", RiskLevel.HIGH, "Elevation 96°"),
        JointResult("Elbow", RiskLevel.LOW, "Flexion 74°"),
        JointResult("Wrist", RiskLevel.MODERATE, "Extension 22°"),
        JointResult("Knee", RiskLevel.LOW, "Flexion 8°")
    )

    val recommendations = listOf(
        "Lower the pallet height to keep loads between knee and shoulder level.",
        "Introduce a lift-assist for boxes above 15 kg.",
        "Rotate workers off this station every 90 minutes."
    )
}
