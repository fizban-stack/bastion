package io.xpipe.app.core;

public abstract class AppNames {

    public static AppNames ofMain() {
        return new Main();
    }

    public static AppNames ofCurrent() {
        if (AppProperties.get() != null && AppProperties.get().isStaging()) {
            return new Ptb();
        } else {
            return new Main();
        }
    }

    public static String propertyName(String name) {
        return ofCurrent().getGroupName() + ".app." + name;
    }

    public static String packageName() {
        return packageName(null);
    }

    public static String packageName(String name) {
        return ofCurrent().getGroupName() + ".app" + (name != null ? "." + name : "");
    }

    public static String extModuleName(String name) {
        return ofCurrent().getGroupName() + ".ext." + name;
    }

    public abstract String getName();

    public abstract String getKebapName();

    public abstract String getSnakeName();

    public abstract String getUppercaseName();

    public abstract String getGroupName();

    public abstract String getExecutableName();

    private static class Main extends AppNames {

        @Override
        public String getName() {
            return "Bastion";
        }

        @Override
        public String getKebapName() {
            return "bastion";
        }

        @Override
        public String getSnakeName() {
            return "bastion";
        }

        @Override
        public String getUppercaseName() {
            return "BASTION";
        }

        @Override
        public String getGroupName() {
            return "io.xpipe";
        }

        @Override
        public String getExecutableName() {
            return "bastiond";
        }
    }

    private static class Ptb extends AppNames {

        @Override
        public String getName() {
            return "Bastion PTB";
        }

        @Override
        public String getKebapName() {
            return "bastion-ptb";
        }

        @Override
        public String getSnakeName() {
            return "bastion_ptb";
        }

        @Override
        public String getUppercaseName() {
            return "BASTION_PTB";
        }

        @Override
        public String getGroupName() {
            return "io.xpipe";
        }

        @Override
        public String getExecutableName() {
            return "bastiond";
        }
    }
}
