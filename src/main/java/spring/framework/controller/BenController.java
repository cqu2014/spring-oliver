package spring.framework.controller;

import cn.hutool.core.lang.Console;
import spring.framework.annotation.GPAutowired;
import spring.framework.annotation.GPController;
import spring.framework.annotation.GPRequestMapping;
import spring.framework.annotation.GPRequestParam;
import spring.framework.service.IBenService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
    public String query(HttpServletRequest request, HttpServletResponse response,
                       @GPRequestParam("name") String name){
        Console.log("{}==>{}","request.getSession().getServletContext()",request.getSession().getServletContext());
        Console.log("{}==>{}","request.getSession().getServletContext().getRealPath(。。)",request.getSession().getServletContext().getRealPath("/"));
        return benService.get(name);
    }

    @GPRequestMapping("/add")
    public String add(HttpServletRequest request,HttpServletResponse response,
                    @GPRequestParam("a") Integer a,@GPRequestParam("b") Integer b){
        return a + " + " + b + " = " + (a + b);
    }
}
