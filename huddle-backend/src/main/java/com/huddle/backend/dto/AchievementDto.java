package com.huddle.backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AchievementDto {
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @Size(max = 50, message = "Date cannot exceed 50 characters")
    private String date;

    public AchievementDto(String title, String description, String date) {
        this.title = title;
        this.description = description;
        this.date = date;
    }
}
