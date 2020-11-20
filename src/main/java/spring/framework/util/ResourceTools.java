package spring.framework.util;

/**
 * @author Oliver Wang
 * @description
 * @created by IntelliJ IDEA 2020.02
 * @date Create at 2020/11/20
 * @since
 */
public class ResourceTools {
    final java.lang.Object $lock = new java.lang.Object[0];

    public static void main(String[] args) {
        System.out.println(ResourceTools.class.getResource(""));
        System.out.println(ResourceTools.class.getResource("/"));
        System.out.println(ResourceTools.class.getResource("/application.yml"));
        System.out.println("##################################################");
        System.out.println(ResourceTools.class.getClassLoader().getResource(""));
        System.out.println(ResourceTools.class.getClassLoader().getResource("/"));
        System.out.println(ResourceTools.class.getResource("/") == ResourceTools.class.getClassLoader().getResource(""));

    }
}
