package com.huddle.backend.dto;

import com.huddle.backend.model.SkillCategory;
import com.huddle.backend.model.SkillProficiency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SkillDto {
    @NotBlank(message = "Skill name cannot be blank")
    @Size(max = 100, message = "Skill name cannot exceed 100 characters")
    private String name;

    private SkillCategory category = SkillCategory.OTHER;

    private SkillProficiency proficiency = SkillProficiency.BEGINNER;

    public SkillDto(String name, SkillCategory category, SkillProficiency proficiency) {
        this.name = name;
        this.category = category != null ? category : SkillCategory.OTHER;
        this.proficiency = proficiency != null ? proficiency : SkillProficiency.BEGINNER;
    }
}
