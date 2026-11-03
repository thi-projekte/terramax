package de.thi.terramax.speech;

import java.nio.file.Path;

/**
 * Wandelt eine Audiodatei in Text um.
 * Die Resource kennt nur dieses Interface, damit der Dienst (Whisper, Cloud) austauschbar bleibt.
 */
public interface SpeechToTextClient {

    /**
     * @throws SpeechToTextException wenn die Erkennung nicht möglich ist
     */
    Transcription transcribe(Path audio, String fileName, String mediaType);
}
