package gs.autopojo.processor.tasks;

import java.lang.annotation.Annotation;

import gs.autopojo.POJO;

class POJOImpl implements POJO {
    private final String value;
    private final boolean builder;

    POJOImpl(String value, boolean builder) {
        this.value = value;
        this.builder = builder;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public boolean builder() {
        return builder;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return POJO.class;
    }

}
