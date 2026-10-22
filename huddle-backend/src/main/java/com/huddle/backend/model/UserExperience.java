package com.huddle.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class UserExperience {

    @Column(name = "title")
    private String title;

    @Column(name = "organization")
    private String organization;

    @Column(name = "start_date")
    private String startDate;

    @Column(name = "end_date")
    private String endDate; // nullable — current role

    @Column(name = "description", length = 1000)
    private String description;

    public UserExperience() {}
}
