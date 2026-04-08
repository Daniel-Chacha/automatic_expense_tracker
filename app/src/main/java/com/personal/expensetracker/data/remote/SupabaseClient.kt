package com.personal.expensetracker.data.remote

import com.personal.expensetracker.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Singleton Supabase client. Configured via BuildConfig fields.
 *
 * To set your credentials, update app/build.gradle.kts:
 *   buildConfigField("String", "SUPABASE_URL", "\"https://xxxx.supabase.co\"")
 *   buildConfigField("String", "SUPABASE_KEY", "\"your-anon-key\"")
 */
object SupabaseClient {

    val instance by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Postgrest)
        }
    }
}
