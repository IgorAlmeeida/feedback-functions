package br.com.fiap.feedback.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FeedbackRequest {

    @JsonProperty("descricao")
    public String description;

    @JsonProperty("nota")
    public Integer score;
}
