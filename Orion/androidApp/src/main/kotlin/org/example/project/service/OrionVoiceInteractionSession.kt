package org.example.project.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import org.example.project.MainActivity

class OrionVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        
        // Launch the main app, passing a flag so it knows to auto-start voice
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("assistant_launch", true)
        }
        
        try {
            // Because this is an assistant session, we must start it specially
            startVoiceActivity(intent)
        } catch (e: Exception) {
            // Fallback if startVoiceActivity fails
            context.startActivity(intent)
        }
        
        // We hide the assistant UI layer because our Activity will handle everything
        hide()
    }
}
