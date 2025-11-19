package com.example.twilio.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Detects audio energy to distinguish between actual speech and silence
 * Mu-law audio: 8-bit samples, where values around 0x7F (127) or 0x00 represent silence
 */
@Component
public class AudioEnergyDetector {
    
    // Silence threshold: average amplitude below this is considered silence
    // Mu-law values range from 0-255, with 127 being zero amplitude
    // Threshold of 10 means we ignore very quiet audio (background noise)
    private static final int SILENCE_THRESHOLD = 10;
    
    // Minimum energy level to consider as actual audio (not silence)
    // This is the RMS (Root Mean Square) threshold
    // Default: 100.0 - more sensitive to detect normal speech
    @Value("${audio.energy.threshold:150.0}")
    private double minEnergyThreshold;
    
    // Minimum percentage of non-silence samples required
    // If less than this percentage has energy, consider it silence
    @Value("${audio.energy.min.non.silence.percent:25.0}")
    private double minNonSilencePercent;
    
    // Getters for diagnostic logging
    public double getMinEnergyThreshold() {
        return minEnergyThreshold;
    }
    
    public double getMinNonSilencePercent() {
        return minNonSilencePercent;
    }
    
    /**
     * Checks if audio chunk contains actual speech (not silence)
     * 
     * @param muLawAudio Raw mu-law encoded audio bytes
     * @return true if audio contains speech, false if silence
     */
    public boolean hasAudioEnergy(byte[] muLawAudio) {
        if (muLawAudio == null || muLawAudio.length == 0) {
            return false;
        }
        
        // Calculate RMS (Root Mean Square) energy
        long sumOfSquares = 0;
        int nonSilenceSamples = 0;
        
        for (byte sample : muLawAudio) {
            // Convert signed byte to unsigned (0-255)
            int unsignedSample = sample & 0xFF;
            
            // Mu-law zero point is around 127 (0x7F)
            // Calculate deviation from silence point
            int deviation = Math.abs(unsignedSample - 127);
            
            // Count non-silence samples
            if (deviation > SILENCE_THRESHOLD) {
                sumOfSquares += (long) deviation * deviation;
                nonSilenceSamples++;
            }
        }
        
        // If no non-silence samples, it's silence
        if (nonSilenceSamples == 0) {
            return false;
        }
        
        // Calculate percentage of non-silence samples
        double nonSilencePercent = (double) nonSilenceSamples / muLawAudio.length * 100.0;
        
        // If too few samples have energy, it's likely background noise, not speech
        if (nonSilencePercent < minNonSilencePercent) {
            return false;
        }
        
        // Calculate RMS energy (only from non-silence samples)
        double rms = Math.sqrt((double) sumOfSquares / nonSilenceSamples);
        
        // Consider it audio only if RMS exceeds threshold
        // This is stricter - we only use RMS, not average amplitude
        boolean hasEnergy = rms > minEnergyThreshold;
        
        return hasEnergy;
    }
    
    /**
     * Calculates the RMS energy level of the audio
     * 
     * @param muLawAudio Raw mu-law encoded audio bytes
     * @return RMS energy value (0 = silence, higher = louder)
     */
    public double calculateEnergy(byte[] muLawAudio) {
        if (muLawAudio == null || muLawAudio.length == 0) {
            return 0.0;
        }
        
        long sumOfSquares = 0;
        int nonSilenceSamples = 0;
        
        for (byte sample : muLawAudio) {
            int unsignedSample = sample & 0xFF;
            int deviation = Math.abs(unsignedSample - 127);
            
            if (deviation > SILENCE_THRESHOLD) {
                sumOfSquares += (long) deviation * deviation;
                nonSilenceSamples++;
            }
        }
        
        if (nonSilenceSamples == 0) {
            return 0.0;
        }
        
        return Math.sqrt((double) sumOfSquares / nonSilenceSamples);
    }
    
    /**
     * Gets the percentage of non-silence samples in the audio
     * 
     * @param muLawAudio Raw mu-law encoded audio bytes
     * @return Percentage (0.0 to 100.0) of non-silence samples
     */
    public double getNonSilencePercentage(byte[] muLawAudio) {
        if (muLawAudio == null || muLawAudio.length == 0) {
            return 0.0;
        }
        
        int nonSilenceCount = 0;
        for (byte sample : muLawAudio) {
            int unsignedSample = sample & 0xFF;
            int deviation = Math.abs(unsignedSample - 127);
            if (deviation > SILENCE_THRESHOLD) {
                nonSilenceCount++;
            }
        }
        
        return (double) nonSilenceCount / muLawAudio.length * 100.0;
    }
}

