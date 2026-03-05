import cn.hutool.crypto.digest.BCrypt;
import org.junit.jupiter.api.Test;

public class PasswordTest {

    @Test
    public void generatePassword() {
        String plainPassword = "123456"; // 你想设置的明文密码

        // 生成哈希
        String passwordHash = BCrypt.hashpw(plainPassword);

        System.out.println("明文密码: " + plainPassword);
        System.out.println("数据库存的值: " + passwordHash);

        // 验证一下是否能通过
        boolean check = BCrypt.checkpw(plainPassword, passwordHash);
        System.out.println("验证结果: " + check);
    }
}