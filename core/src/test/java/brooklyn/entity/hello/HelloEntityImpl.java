package brooklyn.entity.hello;

import brooklyn.entity.basic.AbstractGroupImpl;


public class HelloEntityImpl extends AbstractGroupImpl implements HelloEntity {

    @Override
    public void setAge(Integer age) {
        setAttribute(AGE, age);
        emit(ITS_MY_BIRTHDAY, null);
    }
}
