package edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/** Accepts canonical E.164 numbers recognized by worldwide numbering metadata. */
public final class InternationalPhoneNumberValidator
        implements ConstraintValidator<ValidInternationalPhoneNumber, String> {

    private static final Pattern INTERNATIONAL_NUMBER_FORMAT = Pattern.compile("\\+[1-9][0-9]{1,14}");
    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    @Override
    public boolean isValid(String phoneNumber, ConstraintValidatorContext context) {
        if (phoneNumber == null) {
            return true;
        }
        if (!INTERNATIONAL_NUMBER_FORMAT.matcher(phoneNumber).matches()) {
            return false;
        }

        try {
            PhoneNumber parsedPhoneNumber = PHONE_NUMBER_UTIL.parse(phoneNumber, null);
            return PHONE_NUMBER_UTIL.isValidNumber(parsedPhoneNumber)
                    && phoneNumber.equals(PHONE_NUMBER_UTIL.format(
                    parsedPhoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164));
        } catch (NumberParseException exception) {
            return false;
        }
    }
}
