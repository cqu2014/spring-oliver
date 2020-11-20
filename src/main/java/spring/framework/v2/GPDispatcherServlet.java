package spring.framework.v2;

import cn.hutool.core.lang.Console;
import cn.hutool.json.JSONUtil;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import spring.framework.annotation.*;
import spring.framework.util.StringTool;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Oliver Wang
 * @description GP framework HttpServlet
 * @created by IntelliJ IDEA 2020.02
 * @date Create at 2020/11/17
 * @since 1.0.0
 */
public class GPDispatcherServlet extends HttpServlet {
    /**
     * 配置文件路径
     */
    private static final String CONTEXT_CONFIG_LOCATION = "contextConfigLocation";
    /**
     * bean 所在的包
     */
    private static final String SCAN_PACKAGE = "scanPackage";

    private final Properties contextConfig = new Properties();
    private final List<String> classNames =new ArrayList<>();
    private final Map<String, Object> ioc = new HashMap<>(16);
    private final List<Handler> handlerMapping = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
       doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Console.log("doPost: input req = [{}]", JSONUtil.toJsonStr(req.getParameterMap()));
        //派遣，分发任务
        try {
            //委派模式
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception,Details:\r\n" + Arrays.toString(e.getStackTrace()).replaceAll("\\[|\\]", "").replaceAll(",\\s", "\r\n"));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        Handler handler = getHandler(req);
        if (null == handler){
            resp.getWriter().write("404 not found, oliver here!");
            return;
        }
        // 获取方法的参数列表 考虑保存于handler中
        Class<?>[] parameterTypes = handler.getMethod().getParameterTypes();
        // 需要赋值的参数值 controller中方法的参数要被注解注释关联除了req和resp
        Object[] paramValues = new Object[parameterTypes.length];
        Map<String, String[]> params = req.getParameterMap();
        for (Map.Entry<String, String[]> stringEntry : params.entrySet()) {
            String value = Arrays.toString(stringEntry.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
            if (!handler.getParamIndexMapping().containsKey(stringEntry.getKey())){
                continue;
            }
            Integer index = handler.getParamIndexMapping().get(stringEntry.getKey());
            // 类型转化
            paramValues[index] = convert(parameterTypes[index],value);
        }
        //设置方法中的request和response对象
        if (handler.getParamIndexMapping().containsKey(HttpServletRequest.class.getName())){
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
        }

        if (handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())) {
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
        }

        Object invoke = handler.getMethod().invoke(handler.controller, paramValues);
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding(String.valueOf(StandardCharsets.UTF_8));
        resp.getWriter().write(String.valueOf(invoke));
    }

    /**
     * url传过来的参数都是String类型的，HTTP是基于字符串协议
     * 只需要把String转换为任意类型即可
     * @param type
     * @param value
     * @return
     */
    private Object convert(Class<?> type,String value){
        if (Integer.class == type){
            return Integer.valueOf(value);
        } else if (Double.class == type){
            return Double.valueOf(value);
        } else if (Boolean.class == type){
            return Boolean.valueOf(String.valueOf(type));
        }
        return value;
    }

    private Handler getHandler(HttpServletRequest request){
        if (handlerMapping.isEmpty()){
            return null;
        }
        // /spring-oliver/ben/query
        String url = request.getRequestURI();
        // /spring-oliver
        String contextPath = request.getContextPath();
        // /ben/query
        url = url.replace(contextPath,"").replaceAll("/+","/");

        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.getUrl().matcher(url);
            if (matcher.matches())
                return handler;
        }
        return null;
    }

    /**
     * 初始化 前端控制机器 Servlet
     *
     * @param config
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        /**
         * 模板设计模式
         */
        loadConfig(config.getInitParameter(CONTEXT_CONFIG_LOCATION));
        scanner(contextConfig.getProperty(SCAN_PACKAGE));
        instance();
        autowire();
        initHandlerMapping();

        Console.log("GP Spring framework is init.");
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()){
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(GPController.class)){
                continue;
            }
            String basePath = StringUtils.EMPTY;
            if (clazz.isAnnotationPresent(GPRequestMapping.class)){
                basePath = clazz.getAnnotation(GPRequestMapping.class).value().trim();
            }
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(GPRequestMapping.class)){
                    continue;
                }
                String value = method.getAnnotation(GPRequestMapping.class).value();
                String path = ("/" + basePath + "/" + value).replaceAll("/+","/");
                Pattern pattern = Pattern.compile(path);
                handlerMapping.add(new Handler(entry.getValue(),method,pattern));
                Console.log("Mapped {} => {}",path,method.getName());
            }
        }
    }

    private void autowire() {
        if (ioc.isEmpty()){
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 获取bean的所有属性包括私有
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(GPAutowired.class)){
                    continue;
                }
                GPAutowired annotation = field.getAnnotation(GPAutowired.class);
                String beanName =  StringUtils.EMPTY.equals(annotation.value().trim())? field.getType().getName() :
                        annotation.value().trim();
                // 暴力访问
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void instance() {
        if (classNames.isEmpty()){return;}
        try{
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                // 类名首字母小写
                String beanName = StringTool.toLowerFirstCase(clazz.getSimpleName());
                if (clazz.isAnnotationPresent(GPController.class)){
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);
                }else if (clazz.isAnnotationPresent(GPService.class)){
                    GPService gpService = clazz.getAnnotation(GPService.class);
                    if (!"".equals(gpService.value())){
                        beanName = gpService.value();
                    }
                    Object newInstance = clazz.newInstance();
                    ioc.put(beanName,newInstance);

                    // 建立接口名称与实例的映射 便于接口注入 service真的需要接口吗?????
                    for (Class<?> anInterface : clazz.getInterfaces()) {
                        if (!ioc.containsKey(anInterface.getName())){
                            ioc.put(anInterface.getName(),newInstance);
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
        Console.log("ioc = {}",JSONUtil.toJsonStr(ioc));
    }

    private void scanner(String scanPackage) {
        Console.log("scanner input {}",scanPackage);
        // tomcat 启动此处不能以/开头，jetty启动是否以/开头均可
        String basePackage = scanPackage.replaceAll("\\.","/");
        URL resource = this.getClass().getClassLoader()
                .getResource(basePackage);
        if (resource == null || StringUtils.isEmpty(resource.getFile())){
            return;
        }
        Console.log("scanner resource {}",resource.getFile());
        File classPath = new File(resource.getFile());
        for (File file : Objects.requireNonNull(classPath.listFiles())) {
            if (file.isDirectory()){
                scanner(scanPackage + "." + file.getName());
            }else {
                if (!file.getName().endsWith(".class")){
                    continue;
                }
                // 拼接类权限定名进行保存
                String className = scanPackage + "." + file.getName().replace(".class","");
                classNames.add(className);
            }
        }
        Console.log("classNames = {}",JSONUtil.toJsonStr(classNames));
    }

    /**
     * 加载 application.properties文件
     * @param initParameter "application.properties"
     */
    private void loadConfig(String initParameter) {
        InputStream inputStream = null;
        try {
            inputStream = this.getClass().getClassLoader().getResourceAsStream(initParameter);
            // Properties 对象contextConfig
            contextConfig.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null){
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Data
    private static class Handler {
        private Object controller;
        private Method method;
        private Pattern url;
        private Map<String, Integer> paramIndexMapping;

        public Handler(Object controller,Method method,Pattern url){
            this.controller = controller;
            this.method = method;
            this.url = url;

            this.paramIndexMapping =new HashMap<>(8);
            initParamIndexMapping(this.method);
        }

        /**
         * 初始化参数位置映射表
         *
         * @param method
         */
        private void initParamIndexMapping(Method method) {
            // 此处为二维数组 一个方法有多个参数 一个参数有多个注解
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            // 遍历数组获取 加了注解的参数的位置
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof GPRequestParam){
                        String paramName = ((GPRequestParam) annotation).value();
                        // 该处仅保存了GPRequestParam注解有value值的参数
                        if (!"".equals(paramName)){
                            paramIndexMapping.put(paramName,i);
                        }
                    }
                }
            }
            Console.log("handler:{},paramIndexMapping={}",this.getClass().getName(),JSONUtil.toJsonStr(paramIndexMapping));
            // 获取参数中的 response或request参数
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                if (parameterType == HttpServletRequest.class ||
                        parameterType == HttpServletResponse.class){
                    // 形参的位置以便于实参赋值
                    paramIndexMapping.put(parameterType.getName(),i);
                }
            }
        }
    }
}
