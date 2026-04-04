package org.cekpelunasan.platform.whatsapp.dto.webhook;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

public class MediaPayloadDeserializer extends StdDeserializer<MediaPayloadDTO> {

    public MediaPayloadDeserializer() {
        super(MediaPayloadDTO.class);
    }

    @Override
    public MediaPayloadDTO deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node.isTextual()) {
            MediaPayloadDTO dto = new MediaPayloadDTO();
            dto.setPath(node.asText());
            return dto;
        }
        MediaPayloadDTO dto = new MediaPayloadDTO();
        if (node.has("path")) dto.setPath(node.get("path").asText());
        if (node.has("url")) dto.setUrl(node.get("url").asText());
        if (node.has("caption")) dto.setCaption(node.get("caption").asText());
        return dto;
    }
}
