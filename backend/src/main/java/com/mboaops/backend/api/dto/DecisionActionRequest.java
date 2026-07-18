package com.mboaops.backend.api.dto;

import jakarta.validation.constraints.NotNull;

public class DecisionActionRequest {

    @NotNull
    private ActionPatron action;

    private String commentaire;

    public DecisionActionRequest() {
    }

    public DecisionActionRequest(ActionPatron action, String commentaire) {
        this.action = action;
        this.commentaire = commentaire;
    }

    public ActionPatron getAction() {
        return action;
    }

    public void setAction(ActionPatron action) {
        this.action = action;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }
}
