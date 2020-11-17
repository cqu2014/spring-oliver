package spring.framework.service.impl;

import spring.framework.annotation.GPService;
import spring.framework.service.IBenService;

/**
 * @author Oliver Wang
 * @description
 * @created by IntelliJ IDEA 2020.02
 * @date Create at 2020/11/11
 * @since
 */
@GPService
public class BenServiceImpl implements IBenService {
    @Override
    public String get(String name) {
        return "My name is " + name;
    }
}
