package com.example.twilio.websocket;

import java.util.ArrayList;
import java.util.List;

/**
 * Audio buffer to accumulate audio chunks before processing
 */
public class AudioBuffer {
    private final List<byte[]> chunks = new ArrayList<>();
    private long lastAudioTime = System.currentTimeMillis();
    private long firstAudioTime = 0; // Timestamp of first audio chunk with energy
    private final long silenceTimeoutMs;
    
    public AudioBuffer(long silenceTimeoutMs) {
        this.silenceTimeoutMs = silenceTimeoutMs;
    }
    
    /**
     * Adds an audio chunk to the buffer
     * Only updates lastAudioTime if the chunk contains actual audio (not silence)
     * 
     * @param audioData Audio chunk (mu-law encoded)
     * @param hasEnergy Whether this chunk contains actual audio energy (not silence)
     */
    public void addChunk(byte[] audioData, boolean hasEnergy) {
        synchronized (chunks) {
            chunks.add(audioData);
            // Only update timestamp if there's actual audio energy
            // This prevents silence chunks from resetting the silence timer
            if (hasEnergy) {
                long currentTime = System.currentTimeMillis();
                // Track first audio chunk with energy
                if (firstAudioTime == 0) {
                    firstAudioTime = currentTime;
                }
                lastAudioTime = currentTime;
            }
        }
    }
    
    // Note: Legacy method removed - must use addChunk(byte[], boolean) with AudioEnergyDetector instance
    
    public int getChunkCount() {
        synchronized (chunks) {
            return chunks.size();
        }
    }
    
    public long getTotalBytes() {
        synchronized (chunks) {
            return chunks.stream().mapToLong(arr -> arr.length).sum();
        }
    }
    
    /**
     * Checks if silence has been detected (no audio for the configured timeout period)
     * 
     * @return true if silence timeout has been reached and there are buffered chunks
     */
    public boolean hasSilence() {
        synchronized (chunks) {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastAudio = currentTime - lastAudioTime;
            boolean hasChunks = !chunks.isEmpty();
            boolean timeoutReached = timeSinceLastAudio >= silenceTimeoutMs;
            boolean silenceDetected = timeoutReached && hasChunks;
            
            // Enhanced logging for debugging
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AudioBuffer.class);
            if (silenceDetected) {
                logger.info(">>> hasSilence() = TRUE: timeSinceAudio={}ms (timeout={}ms), chunks={}, bytes={}", 
                           timeSinceLastAudio, silenceTimeoutMs, chunks.size(), getTotalBytes());
            } else {
                logger.debug(">>> hasSilence() = FALSE: timeSinceAudio={}ms (timeout={}ms), hasChunks={}, timeoutReached={}", 
                           timeSinceLastAudio, silenceTimeoutMs, hasChunks, timeoutReached);
            }
            
            return silenceDetected;
        }
    }
    
    /**
     * Gets the time since last audio chunk was received
     * 
     * @return milliseconds since last audio
     */
    public long getTimeSinceLastAudio() {
        return System.currentTimeMillis() - lastAudioTime;
    }
    
    /**
     * Gets the total duration of audio captured (from first to last audio chunk with energy)
     * 
     * @return milliseconds of audio duration, or 0 if no audio captured yet
     */
    public long getAudioDuration() {
        synchronized (chunks) {
            if (firstAudioTime == 0 || chunks.isEmpty()) {
                return 0;
            }
            // Duration is from first audio to last audio
            return lastAudioTime - firstAudioTime;
        }
    }
    
    public byte[] getBufferedAudio() {
        synchronized (chunks) {
            if (chunks.isEmpty()) {
                return null;
            }
            
            // Calculate total size
            int totalSize = chunks.stream().mapToInt(arr -> arr.length).sum();
            byte[] result = new byte[totalSize];
            
            // Copy all chunks into result
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            
            // Clear buffer
            chunks.clear();
            lastAudioTime = System.currentTimeMillis();
            firstAudioTime = 0; // Reset first audio time
            
            return result;
        }
    }
    
    public void clear() {
        synchronized (chunks) {
            chunks.clear();
            lastAudioTime = System.currentTimeMillis();
            firstAudioTime = 0; // Reset first audio time
        }
    }
    
    public boolean isEmpty() {
        synchronized (chunks) {
            return chunks.isEmpty();
        }
    }
}

