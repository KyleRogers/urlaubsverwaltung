/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.synyx.urlaubsverwaltung.web.validator;

import org.apache.log4j.Logger;

import org.joda.time.DateMidnight;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

import org.springframework.ui.Model;

import org.springframework.util.StringUtils;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.synyx.urlaubsverwaltung.core.application.domain.Comment;
import org.synyx.urlaubsverwaltung.core.application.domain.DayLength;
import org.synyx.urlaubsverwaltung.core.application.domain.VacationType;
import org.synyx.urlaubsverwaltung.core.util.PropertiesUtil;
import org.synyx.urlaubsverwaltung.web.application.AppForm;

import java.util.Properties;


/**
 * This class validate if an {@link AppForm} is filled correctly by the user, else it saves error messages in errors
 * object.
 *
 * @author  Aljona Murygina
 */
@Component
public class ApplicationValidator implements Validator {

    // logs general errors like "properties file not found"
    private static final Logger LOG = Logger.getLogger("errorLog");

    // errors' properties keys
    private static final String MANDATORY_FIELD = "error.mandatory.field";
    private static final String ERROR_REASON = "error.reason";
    private static final String ERROR_PERIOD = "error.period";
    private static final String ERROR_PAST = "error.period.past";
    private static final String ERROR_LENGTH = "error.length";

    // names of fields
    private static final String START_DATE = "startDate";
    private static final String END_DATE = "endDate";
    private static final String START_DATE_HALF = "startDateHalf";
    private static final String REASON = "reason";
    private static final String ADDRESS = "address";
    private static final String TEXT = "reason";

    private static final String CUSTOM_PROPERTIES_FILE = "custom.properties";
    private Properties customProperties;
    private PropertiesValidator propValidator;

    @Autowired
    public ApplicationValidator(PropertiesValidator propValidator) {

        this.propValidator = propValidator;

        try {
            this.customProperties = PropertiesUtil.load(CUSTOM_PROPERTIES_FILE);
        } catch (Exception ex) {
            LOG.error("No properties file found.");
            LOG.error(ex.getMessage(), ex);
        }
    }

    @Override
    public boolean supports(Class<?> clazz) {

        return AppForm.class.equals(clazz);
    }


    @Override
    public void validate(Object target, Errors errors) {

        AppForm app = (AppForm) target;

        // check if date fields are valid
        validateDateFields(app, errors);

        // check if reason is not filled
        if (app.getVacationType() != VacationType.HOLIDAY) {
            if (!StringUtils.hasText(app.getReason())) {
                errors.rejectValue(REASON, MANDATORY_FIELD);
            }
        }

        validateStringFields(app, errors);
    }


    private void validateDateFields(AppForm app, Errors errors) {

        if (app.getHowLong() == DayLength.FULL) {
            // check if date fields are not filled
            if (app.getStartDate() == null) {
                if (errors.getFieldErrors(START_DATE).isEmpty()) {
                    errors.rejectValue(START_DATE, MANDATORY_FIELD);
                }
            }

            if (app.getEndDate() == null) {
                if (errors.getFieldErrors(END_DATE).isEmpty()) {
                    errors.rejectValue(END_DATE, MANDATORY_FIELD);
                }
            }

            if (app.getStartDate() != null && app.getEndDate() != null) {
                // check if from < to
                if (app.getStartDate().isAfter(app.getEndDate())) {
                    errors.reject(ERROR_PERIOD);
                } else {
                    // applying for leave maximum permissible x months in advance
                    propValidator.validateMaximumVacationProperty(customProperties, app, errors);
                }
            }
        } else {
            if (app.getStartDateHalf() == null) {
                if (errors.getFieldErrors(START_DATE_HALF).isEmpty()) {
                    errors.rejectValue(START_DATE_HALF, MANDATORY_FIELD);
                }
            }
        }
    }


    /**
     * Check if application's period is in the past; to be able to display a warning message. ("Period is in the past.
     * Are you sure?")
     *
     * @param  target
     * @param  errors
     */
    public void validatePast(Object target, Errors errors, Model model) {

        AppForm app = (AppForm) target;

        DateMidnight startDate;

        if (app.getHowLong() == DayLength.FULL) {
            startDate = app.getStartDate();
        } else {
            startDate = app.getStartDateHalf();
        }

        if (startDate != null) {
            DateMidnight now = DateMidnight.now();

            DateMidnight todaysMidnight = new DateMidnight(now.getYear(), now.getMonthOfYear(), now.getDayOfMonth());

            if (startDate.isBefore(todaysMidnight)) {
                if (startDate.isBefore(DateMidnight.now().minusYears(1))) {
                    model.addAttribute("setForce", 0);
                    model.addAttribute("timeError", "error.period.past.wide");
                } else {
                    model.addAttribute("timeError", ERROR_PAST);
                    model.addAttribute("setForce", 1);
                }
            }
        }
    }


    /**
     * Validates if comment field is filled (mandatory if application is rejected by boss).
     *
     * @param  target
     * @param  errors
     * @param  mandatory  (true e.g. if application is rejected by boss, false e.g. if user cancels his own application)
     */
    public void validateComment(Object target, Errors errors, boolean mandatory) {

        Comment comment = (Comment) target;

        if (StringUtils.hasText(comment.getReason())) {
            if (!validateStringLength(comment.getReason(), 200)) {
                errors.rejectValue(TEXT, ERROR_LENGTH);
            }
        } else {
            if (mandatory) {
                errors.rejectValue(TEXT, ERROR_REASON);
            }
        }
    }


    /**
     * Check if String fields of application form have a valid length.
     *
     * @param  app
     * @param  errors
     */
    private void validateStringFields(AppForm app, Errors errors) {

        validateStringField(app.getReason(), REASON, errors);

        validateStringField(app.getAddress(), ADDRESS, errors);

        validateStringField(app.getComment(), "comment", errors);
    }


    /**
     * Ensure that string field is not empty and has not more than 200 chars.
     *
     * @param  text
     * @param  field
     * @param  errors
     */
    private void validateStringField(String text, String field, Errors errors) {

        if (StringUtils.hasText(text)) {
            if (!validateStringLength(text, 200)) {
                errors.rejectValue(field, ERROR_LENGTH);
            }
        }
    }


    /**
     * Checks if a String has a valid length.
     *
     * @param  string
     *
     * @return
     */
    protected boolean validateStringLength(String string, int length) {

        if (string.length() > length) {
            return false;
        } else {
            return true;
        }
    }


    /**
     * Validation for converting sick note to vacation: {@link AppForm} only reason and vacation type must be validated.
     *
     * @param  app
     * @param  errors
     */
    public void validatedShortenedAppForm(AppForm app, Errors errors) {

        if (!StringUtils.hasText(app.getReason())) {
            errors.rejectValue(REASON, MANDATORY_FIELD);
        } else {
            validateStringField(app.getReason(), REASON, errors);
        }
    }
}