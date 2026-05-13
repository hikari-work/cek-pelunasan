package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "min_bunga_session")
public class MinBungaSession {

    public static final long TTL_SECONDS = 30 * 60;

    @Id
    private String chatId;

    private String identifier;

    private String role;

    @Builder.Default
    private List<String> selectedDates = new ArrayList<>();

    @Indexed(expireAfterSeconds = (int) TTL_SECONDS)
    private LocalDateTime createdAt;
}
