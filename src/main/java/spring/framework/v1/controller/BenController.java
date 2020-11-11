package spring.framework.v1.controller;

import cn.hutool.core.lang.Console;
import spring.framework.v1.annotation.GPAutowired;
import spring.framework.v1.annotation.GPController;
import spring.framework.v1.annotation.GPRequestMapping;
import spring.framework.v1.annotation.GPRequestParam;
import spring.framework.v1.service.IBenService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Oliver Wang
 * @description
 * @created by IntelliJ IDEA 2020.02
 * @date Create at 2020/11/11
 * @since
 */
@GPController
@GPRequestMapping("/ben")
public class BenController {
    @GPAutowired
    private IBenService benService;

    @GPRequestMapping("/query")
    private void query(HttpServletRequest request, HttpServletResponse response,
                       @GPRequestParam String name){
        String result = benService.get(name);

        try {
            response.getWriter().write(result);
        } catch (IOException e) {
            Console.error("query with {} error:{}",name,e.getLocalizedMessage());
        }
    }

    public void add(HttpServletRequest request,HttpServletResponse response,
                    @GPRequestParam("a") Integer a,@GPRequestParam("b") Integer b){
        try {
            StringBuilder stringBuilder = new StringBuilder(a);
            stringBuilder.append("+").append(b).append("=").append(a+b);
            response.getWriter().write(stringBuilder.toString());
        } catch (IOException e) {
            Console.error("query with {} and {} error:{}",a,b,e.getLocalizedMessage());
        }
    }
}
