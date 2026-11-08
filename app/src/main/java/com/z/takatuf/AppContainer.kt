package com.z.takatuf

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.z.takatuf.data.AiRepository
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.AuthRepository
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.ProfileRepository
import com.z.takatuf.data.SavedRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.TeamRepository
import com.z.takatuf.data.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency wiring: one instance of each repository for the whole app. */
class AppContainer(
    context: Context,
    auth: FirebaseAuth = FirebaseAuth.getInstance(),
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val authRepository = AuthRepository(auth)
    val profiles = ProfileRepository(db)
    val listings = ListingRepository(db)
    val applications = ApplicationRepository(db)
    val saved = SavedRepository(db)
    val teams = TeamRepository(db)
    val workspace = WorkspaceRepository(db)
    val ai = AiRepository(
        apiKey = BuildConfig.OPENAI_API_KEY,
        model = BuildConfig.OPENAI_MODEL,
        dailyLimit = BuildConfig.AI_DAILY_LIMIT,
        prefs = context.getSharedPreferences(AiRepository.PREFS, Context.MODE_PRIVATE),
    )
    val session = Session(authRepository, profiles, appScope)
}
