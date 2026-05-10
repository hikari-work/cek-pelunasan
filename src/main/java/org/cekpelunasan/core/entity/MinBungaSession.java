package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
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

    @Id
    private String chatId;

    private String identifier;

    private String role;

    @Builder.Default
    private List<String> selectedDates = new ArrayList<>();

    private LocalDateTime createdAt;
}
