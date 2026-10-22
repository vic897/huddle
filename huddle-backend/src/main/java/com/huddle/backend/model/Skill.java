package com.huddle.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Structured skill entry stored in the user_skills join table.
 * Columns match the migrated schema: name, category, proficiency.
 * The old plain-string 'skill' column was renamed to 'name' via SQL migration
 * before this @Embeddable was introduced — existing data is preserved.
 */
@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class Skill {

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private SkillCategory category = SkillCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency")
    private SkillProficiency proficiency = SkillProficiency.BEGINNER;

    // Required by JPA
    public Skill() {}

    public Skill(String name, SkillCategory category, SkillProficiency proficiency) {
        this.name = name;
        this.category = category;
        this.proficiency = proficiency;
    }

    /** Convenience constructor for when only a name is known (e.g. legacy import). */
    public Skill(String name) {
        this.name = name;
        this.category = SkillCategory.OTHER;
        this.proficiency = SkillProficiency.BEGINNER;
    }
}
