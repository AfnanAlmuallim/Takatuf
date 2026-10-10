package com.z.takatuf.data

/**
 * Suggestions that make forms quicker to fill in. Every value stays editable; nothing here
 * limits what a student can type.
 */
object Catalog {

    val majors = listOf(
        "Software Engineering",
        "Software Engineering (AI)",
        "Software Engineering (Cybersecurity)",
        "Computer Science",
        "Information Systems",
        "Cybersecurity",
        "Data Science",
        "Electrical Engineering",
        "Engineering Management",
        "Communication and Networks Engineering",
        "Architecture",
        "Interior Design",
        "Accounting",
        "Finance",
        "Marketing",
        "Management",
        "Law",
        "Translation",
    )

    val skills = listOf(
        "Python", "Java", "Kotlin", "Swift", "JavaScript", "TypeScript", "C++", "C#", "Dart", "SQL", "R",
        "Android", "Jetpack Compose", "Flutter", "React", "React Native", "Node.js", "Django", "FastAPI",
        "Spring Boot", "HTML and CSS", "REST APIs", "Firebase", "Git",
        "Machine learning", "Deep learning", "Data analysis", "Data visualization", "NLP",
        "Computer vision", "PyTorch", "TensorFlow", "Statistics", "Power BI", "Excel",
        "Network security", "Penetration testing", "Threat modeling", "Secure coding", "Cryptography",
        "Digital forensics", "Linux",
        "Figma", "UI design", "UX research", "Prototyping", "Usability testing", "Graphic design",
        "Illustration", "Motion design", "Video editing", "3D modeling",
        "Market research", "Business model", "Pitching", "Financial modeling", "Marketing",
        "Social media", "Copywriting", "Project management", "Agile and Scrum", "Public speaking",
        "Arduino", "Embedded C", "IoT", "Circuit design", "CAD", "Robotics",
        "Technical writing", "Research", "Arabic–English translation", "Legal research", "AutoCAD", "Revit",
    )

    private val skillFocus: List<Pair<Regex, List<String>>> = listOf(
        Regex("cyber|security", RegexOption.IGNORE_CASE) to listOf(
            "Network security", "Penetration testing", "Threat modeling", "Secure coding", "Cryptography",
            "Digital forensics", "Linux", "Python",
        ),
        Regex("\\(ai\\)|data|artificial", RegexOption.IGNORE_CASE) to listOf(
            "Python", "Machine learning", "Deep learning", "Data analysis", "Data visualization", "NLP",
            "Computer vision", "PyTorch", "SQL", "Statistics",
        ),
        Regex("electrical|communication|network|engineering management", RegexOption.IGNORE_CASE) to listOf(
            "Arduino", "Embedded C", "IoT", "Circuit design", "CAD", "Robotics", "Python", "Project management",
        ),
        Regex("architect|interior", RegexOption.IGNORE_CASE) to listOf(
            "AutoCAD", "Revit", "3D modeling", "Graphic design", "Illustration", "Figma", "Prototyping",
        ),
        Regex("market|finance|account|management|business", RegexOption.IGNORE_CASE) to listOf(
            "Market research", "Business model", "Pitching", "Financial modeling", "Marketing", "Social media",
            "Copywriting", "Public speaking", "Excel", "Project management",
        ),
        Regex("law|translation", RegexOption.IGNORE_CASE) to listOf(
            "Legal research", "Technical writing", "Research", "Arabic–English translation", "Public speaking",
        ),
    )

    /** All suggestions, with the ones most students of [major] use listed first. */
    fun skillsFor(major: String): List<String> {
        val focus = skillFocus.firstOrNull { (pattern, _) -> pattern.containsMatchIn(major) }?.second.orEmpty()
        return (focus + skills).distinct()
    }

    val interests = listOf(
        "Artificial intelligence", "Health", "Education", "Fintech", "Sustainability", "Smart cities",
        "Games", "Social impact", "Cybersecurity", "Robotics", "E-commerce", "Arts and culture",
        "Sports", "Accessibility", "Tourism", "Energy", "Mobility", "Space", "Law and policy",
        "Entrepreneurship",
    )

    val projectDurations = listOf("4 weeks", "8 weeks", "One semester", "Two semesters")

    val teamDurations = listOf("Until the event") + projectDurations

    val hoursPerWeek = listOf(2, 4, 6, 8, 10, 15, 20)
}

/** Editable starting points for describing a role (FR08). Picking one fills the role form. */
data class RoleTemplate(val title: String, val description: String, val skills: List<String>)

object RoleTemplates {
    val all = listOf(
        RoleTemplate(
            "Mobile developer",
            "Builds the Android app and connects it to the backend.",
            listOf("Kotlin", "Jetpack Compose", "Firebase", "REST APIs"),
        ),
        RoleTemplate(
            "Frontend developer",
            "Builds the web interface and keeps it fast and accessible.",
            listOf("React", "TypeScript", "HTML and CSS"),
        ),
        RoleTemplate(
            "Backend developer",
            "Designs the API and database and keeps data safe.",
            listOf("Python", "FastAPI", "SQL", "REST APIs"),
        ),
        RoleTemplate(
            "UI/UX designer",
            "Researches users, designs the flows and tests them with real students.",
            listOf("Figma", "UX research", "Prototyping", "Usability testing"),
        ),
        RoleTemplate(
            "Data analyst",
            "Cleans the data, finds the story in it and presents it clearly.",
            listOf("Python", "SQL", "Data analysis", "Data visualization"),
        ),
        RoleTemplate(
            "Machine learning engineer",
            "Trains, evaluates and ships the models the project depends on.",
            listOf("Python", "Machine learning", "PyTorch", "Statistics"),
        ),
        RoleTemplate(
            "Cybersecurity specialist",
            "Finds weaknesses early and makes sure the system is secure by design.",
            listOf("Threat modeling", "Secure coding", "Network security", "Penetration testing"),
        ),
        RoleTemplate(
            "Hardware and IoT engineer",
            "Builds and wires the physical prototype and its sensors.",
            listOf("Arduino", "Embedded C", "IoT", "Circuit design"),
        ),
        RoleTemplate(
            "Business and pitch lead",
            "Shapes the business case and presents it to judges and users.",
            listOf("Market research", "Business model", "Pitching", "Public speaking"),
        ),
        RoleTemplate(
            "Project coordinator",
            "Plans the work, runs check-ins and keeps everyone on schedule.",
            listOf("Project management", "Agile and Scrum", "Technical writing"),
        ),
        RoleTemplate(
            "Content creator",
            "Writes, films and edits the story of the project.",
            listOf("Copywriting", "Video editing", "Social media", "Graphic design"),
        ),
        RoleTemplate(
            "Researcher",
            "Reviews the literature, gathers data and writes up the findings.",
            listOf("Research", "Technical writing", "Statistics"),
        ),
    )
}
