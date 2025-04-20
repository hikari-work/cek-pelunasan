package org.cekpelunasan.entity;

import jakarta.annotation.Nullable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity(name = "users")
public class User {

    @Id
    private Long chatId;

    @Nullable
    private String userCode;

    @Nullable
    private String branch;

    @Nullable
    @Enumerated(EnumType.STRING)
    private AccountOfficerRoles roles;


}
