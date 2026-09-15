package com.appfactory.modules.validation;

import java.util.regex.Pattern;

/**
 * Lightweight composable validators. Each rule returns a Validation.Result;
 * failures carry a stable key for i18n and a message.
 */
public final class Validator {

    private Validator() { }

    public static final class Result {
        public final boolean ok;
        public final String key;
        public final String message;

        private Result(boolean ok, String key, String message) {
            this.ok = ok; this.key = key; this.message = message;
        }
        public static final Result OK = new Result(true, "", "");
        public static Result fail(String key, String message) {
            return new Result(false, key, message);
        }
    }

    public interface Rule {
        Result check(String value);
    }

    public static Result all(String value, Rule... rules) {
        for (Rule r : rules) {
            Result res = r.check(value);
            if (!res.ok) return res;
        }
        return Result.OK;
    }

    public static Rule required(String message) {
        return v -> (v != null && !v.trim().isEmpty())
                ? Result.OK : Result.fail("required", message);
    }

    public static Rule minLength(int min, String message) {
        return v -> (v != null && v.length() >= min)
                ? Result.OK : Result.fail("minLength", message);
    }

    public static Rule maxLength(int max, String message) {
        return v -> (v != null && v.length() <= max)
                ? Result.OK : Result.fail("maxLength", message);
    }

    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public static Rule email(String message) {
        return v -> (v != null && EMAIL.matcher(v).matches())
                ? Result.OK : Result.fail("email", message);
    }

    public static Rule range(double min, double max, String message) {
        return v -> {
            if (v == null) return Result.fail("range", message);
            try {
                double d = Double.parseDouble(v.trim());
                return (d >= min && d <= max)
                        ? Result.OK : Result.fail("range", message);
            } catch (NumberFormatException e) {
                return Result.fail("range", message);
            }
        };
    }

    public static Rule matches(Pattern p, String message) {
        return v -> (v != null && p.matcher(v).matches())
                ? Result.OK : Result.fail("pattern", message);
    }

    public static Rule oneOf(String[] allowed, String message) {
        return v -> {
            if (v == null) return Result.fail("oneOf", message);
            for (String a : allowed) if (a.equals(v)) return Result.OK;
            return Result.fail("oneOf", message);
        };
    }
}