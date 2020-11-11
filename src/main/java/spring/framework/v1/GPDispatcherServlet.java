package spring.framework.v1;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Oliver Wang
 * @description
 * @created by IntelliJ IDEA 2020.02
 * @date Create at 2020/11/11
 * @since
 */
public class GPDispatcherServlet extends HttpServlet {

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
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");
        // 未找到请求url对应的方法则返回 404状态码
        if (!handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found!!");
            return;
        }
        Method method = handlerMapping.get(url);
        Map<String, String[]> params = req.getParameterMap();
        Class<?>[] parameterTypes = method.getParameterTypes();
        Map<String, String[]> parameterMap = req.getParameterMap();
        // 保存赋值参数的位置
        Object[] paramValues = new Object[parameterTypes.length];
    }

    @Override
    public void init() throws ServletException {
        super.init();
    }
}
