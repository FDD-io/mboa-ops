package com.mboaops.backend.api;

import com.mboaops.backend.api.dto.MessageRequest;
import com.mboaops.backend.api.dto.MessageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageIngestionService messageIngestionService;

    public MessageController(MessageIngestionService messageIngestionService) {
        this.messageIngestionService = messageIngestionService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse receive(@Valid @RequestBody MessageRequest request) {
        return messageIngestionService.ingest(request);
    }

    /**
     * Variante multipart : accepte un vocal et/ou une photo de liste
     * manuscrite (convertis en base64 avant transmission aux modèles Qwen),
     * plus un texte optionnel.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse receiveMultipart(
            @RequestParam("clientPhone") String clientPhone,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        if (clientPhone == null || clientPhone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientPhone est obligatoire");
        }
        if ((audio == null || audio.isEmpty()) && (image == null || image.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Au moins un fichier audio ou image est requis (sinon utiliser la variante JSON)");
        }

        String audioBase64 = null;
        String audioFormat = null;
        if (audio != null && !audio.isEmpty()) {
            audioBase64 = toBase64(audio);
            audioFormat = detecterFormatAudio(audio);
        }

        String imageBase64 = null;
        String imageMimeType = null;
        if (image != null && !image.isEmpty()) {
            imageBase64 = toBase64(image);
            imageMimeType = image.getContentType() != null ? image.getContentType() : "image/jpeg";
        }

        return messageIngestionService.ingestMultimodal(
                clientPhone, content, audioBase64, audioFormat, imageBase64, imageMimeType);
    }

    private String toBase64(MultipartFile fichier) {
        try {
            return Base64.getEncoder().encodeToString(fichier.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fichier illisible : " + fichier.getOriginalFilename(), e);
        }
    }

    private String detecterFormatAudio(MultipartFile audio) {
        String nom = audio.getOriginalFilename();
        if (nom != null && nom.contains(".")) {
            String extension = nom.substring(nom.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (!extension.isBlank()) {
                return extension;
            }
        }
        String contentType = audio.getContentType();
        if (contentType != null && contentType.startsWith("audio/")) {
            return contentType.substring("audio/".length());
        }
        return "mp3";
    }
}
