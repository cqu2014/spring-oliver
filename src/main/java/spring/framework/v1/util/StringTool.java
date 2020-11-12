package spring.framework.v1.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

/**
 * @author Oliver Wang
 * @description
 * @created by IntelliJ IDEA 2020.02
 * @date Create at 2020/11/12
 * @since
 */
public final class StringTool {
    public static void main(String[] args) {
        int[] digits = new int[]{1,2,3,4,5,6,7,8,9};
        int[] result = new int[digits.length+1];
        System.arraycopy(digits,0,result,1,digits.length);
        result[0] = 9;
        System.out.println(Arrays.toString(result));
    }

    /**
     * 将首字母大写的字符串转化为首字母小写的字符创
     *
     * @param simpleName JumpMonkey
     * @return jumMonkey
     */
    public static String toLowerFirstCase(String simpleName){
        if (StringUtils.isBlank(simpleName)){
            return simpleName;
        }
        char[] chars = simpleName.toCharArray();
        // 转化为小写
        chars[0] += 32;
        return String.valueOf(chars);
    }
}
