package spring.framework.v1;

import cn.hutool.core.lang.Console;
import cn.hutool.extra.ssh.JschUtil;
import cn.hutool.json.JSONUtil;
import spring.framework.v1.annotation.*;
import spring.framework.v1.util.StringTool;

import javax.servlet.ServletConfig;
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
import java.util.*;

/**
 * @author Oliver Wang
 * @description GP前端控制器
 * @created by IntelliJ IDEA 2020.02
 * @date Create at 2020/11/11
 * @since
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

    /**
     * 存储application.properties 设置的内容
     */
    private final Properties contextConfig = new Properties();

    /**
     * 存储所有扫描到的类名
     */
    private final List<String> classNames = new ArrayList<>();

    /**
     * IOC容器 保存所有实例化对象 注册式单例
     */
    private final Map<String,Object> ioc = new HashMap<>(64);

    /**
     * 保存Controller中所有Mapping的对应关系
     */
    private final Map<String, Method> handlerMapping = new HashMap<>(4);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Console.log("doPost with req = {}", JSONUtil.toJsonStr(req.getParameterMap()));
        try {
            doDispatch(req,resp);
        } catch (Exception exception) {
            resp.getWriter().write("500 Exception,Details:\r\n" + Arrays.toString(exception.getStackTrace()).replaceAll("\\[|\\]", "").replaceAll(",\\s", "\r\n"));
        }
    }

    /**
     * 前端控制器--委派任务执行器
     *
     * @param req
     * @param resp
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");
        // 未找到请求url对应的方法则返回 404状态码
        if (!handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found!! Written by oliver wang");
            resp.getWriter().write(JSONUtil.toJsonStr(handlerMapping));
            return;
        }
        // 获取handlerMapping中对应的方法
        Method method = handlerMapping.get(url);
        // 获取请求中携带的实参
        Map<String, String[]> params = req.getParameterMap();
        // 方法上形参的类型
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 保存请求的url参数列表
        Map<String, String[]> parameterMap = req.getParameterMap();
        // 保存赋值参数的位置
        Object[] paramValues = new Object[parameterTypes.length];
        // 按照形参位置动态赋值
        for (int i = 0; i < parameterTypes.length; i++) {
            // isInstance
            if (parameterTypes[i] == HttpServletRequest.class){
                paramValues[i] = req;
            }else if (parameterTypes[i] == HttpServletResponse.class){
                paramValues[i] =resp;
            }else if (parameterTypes[i] == String.class){
                // 提取方法中加了注解的参数
                Annotation[][] methodParameterAnnotations = method.getParameterAnnotations();
                for (int j = 0; j < methodParameterAnnotations.length; j++) {
                    for (Annotation annotation : methodParameterAnnotations[i]){
                        if (annotation instanceof GPRequestParam){
                            String paramName = ((GPRequestParam) annotation).value();
                            if (!"".equals(paramName.trim())){
                                String value = Arrays.toString(parameterMap.get(paramName))
                                        .replaceAll("[\\[\\]]", "")
                                        .replaceAll("\\s", ",");
                                paramValues[i] = value;
                            }
                        }
                    }
                }
            }
        }

        // 通过反射获取method的class，then 获取class的name
        String beanName = StringTool.toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        try {
            if (params == null || params.get("name") == null
                    || params.get("name")[0] == null){
                method.invoke(ioc.get(beanName),req,resp);
            }else {
                method.invoke(ioc.get(beanName),req,resp,params.get("name")[0]);
            }
        }catch (Exception exception){
            exception.printStackTrace();
        }
    }

    @Override
    public void init(ServletConfig config) {
        // 1. 加载配置文件 至 contextConfig
        doLoadConfig(config.getInitParameter(CONTEXT_CONFIG_LOCATION));

        // 2. 扫描相关类
        doScanner(contextConfig.getProperty(SCAN_PACKAGE));

        // 3. 初始化所有相关的类实例，并且放入到IOC容器之中
        doInstance();

        // 4. 完成依赖注入
        doAutoWire();

        // 5. 初始化HandlerMapping
        initHandlerMapping();
        
        Console.log("GP Spring framework is init");
    }

    /**
     * 初始化 mvc框架的 HandlerMapping
     */
    private void initHandlerMapping() {
        if (ioc.isEmpty()) return;
        Console.log("initHandlerMapping ioc={}",JSONUtil.toJsonStr(ioc));

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> aClass = entry.getValue().getClass();
            if (!aClass.isAnnotationPresent(GPController.class)){
                continue;
            }
            String baseUrl = "";
            //  获取controller类上配置的路径信息--拼接
            if (aClass.isAnnotationPresent(GPRequestMapping.class)){
                baseUrl = aClass.getAnnotation(GPRequestMapping.class).value();
            }

            Method[] methods = aClass.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(GPRequestMapping.class)){
                    continue;
                }
                GPRequestMapping methodAnnotation = method.getAnnotation(GPRequestMapping.class);
                String url = ("/" + baseUrl + "/" + methodAnnotation.value()).replaceAll("/+", "/");
                handlerMapping.put(url,method);
                Console.log("Mapped {}==> {}",url,method);
            }
        }
    }

    /**
     * 完成自动注入功能
     */
    private void doAutoWire() {
        if (ioc.isEmpty()){
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 获取实例对象的所有属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(GPAutowired.class)){
                    continue;
                }
                GPAutowired gpAutowired = field.getAnnotation(GPAutowired.class);
                //  获取需要注入的属性的 beanName
                String beanName = "".equals(gpAutowired.value()) ? field.getType().getName() : gpAutowired.value();
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doInstance() {
        if (classNames.isEmpty()){
            return;
        }
        try {
            for (String className : classNames) {
                Class<?> aClass = Class.forName(className);
                if (aClass.isAnnotationPresent(GPController.class)){
                    Object instance = aClass.newInstance();
                    String beanName = StringTool.toLowerFirstCase(aClass.getSimpleName());
                    ioc.put(beanName,instance);
                    Console.log("Instance {} success!,instance={}",beanName,instance.getClass().getSimpleName());
                }else if (aClass.isAnnotationPresent(GPService.class)){
                    String beanName = StringTool.toLowerFirstCase(aClass.getSimpleName());

                    GPService annotation = aClass.getAnnotation(GPService.class);
                    if (!"".equals(annotation.value())){
                        beanName = annotation.value();
                    }
                    Object instance = aClass.newInstance();
                    ioc.put(beanName, instance);
                    Console.log("Instance {} success!,instance={}",beanName,instance.getClass().getSimpleName());
                    // 为了后期根据类型注入实现类的一种笨方法
                    for (Class<?> anInterface : aClass.getInterfaces()) {
                        if (ioc.containsKey(anInterface.getName())) {
                            throw new Exception("The beanName is exists!!");
                        }
                        ioc.put(anInterface.getName(), instance);
                    }
                }
            }
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            Console.error("doInstance error:{}",e.getLocalizedMessage());
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        Console.log("Ioc = {}",JSONUtil.toJsonStr(ioc));
    }

    /**
     * 扫描对应.class 文件并缓存 beanNames
     *
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {
        String baseUrl = "/" + scanPackage.replaceAll("\\.","/");
        // 获取绝对路径
        URL url = this.getClass().getClassLoader().getResource(baseUrl);
        if (url == null || url.getFile() == null) {
            return;
        }
        File classPath = new File(url.getFile());
        for (File file : Objects.requireNonNull(classPath.listFiles())) {
            if (file.isDirectory()){
                // 递归扫描文件夹
                doScanner(scanPackage + "." + file.getName() );
            }else {
                if(!file.getName().endsWith(".class")){
                    continue;
                }
                String className = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }
        Console.log("Scanner package {} over,classNames={}",scanPackage,JSONUtil.toJsonStr(classNames));
    }

    /**
     * 加载配置文件
     * @param initParameter
     */
    private void doLoadConfig(String initParameter) {
        try (InputStream inputStream = this.getClass().getClassLoader()
                .getResourceAsStream(initParameter)){
            contextConfig.load(inputStream);
            Console.log("contextConfig = {}",JSONUtil.toJsonStr(contextConfig));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
