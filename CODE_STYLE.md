# 代码规范检查指南

本项目使用 Checkstyle 和 Spotless 来保证代码质量和统一的代码风格。

## 工具说明

### 1. Checkstyle
用于检查 Java 代码是否符合编码规范，包括：
- 禁止使用 `printStackTrace()`
- 禁止使用星号导入（除特定包外）
- 类必须有 Javadoc 注释
- 命名规范检查
- 代码格式检查

配置文件位置：
- `checkstyle/shortlink_checkstyle.xml` - 主配置文件
- `checkstyle/shortlink_checkstyle_suppression.xml` - 抑制规则配置

### 2. Spotless
用于自动格式化代码，包括：
- Eclipse 格式化规则
- 自动添加 Apache License 2.0 版权声明
- 移除未使用的导入
- 统一导入顺序

配置文件位置：
- `format/shortlink_spotless_formatter.xml` - 格式化规则
- `format/copyright.txt` - 版权声明模板

## 使用方法

### 检查代码规范

```bash
# 运行 Checkstyle 检查
mvn checkstyle:check

# 运行 Spotless 检查
mvn spotless:check
```

### 自动修复代码格式

```bash
# 使用 Spotless 自动格式化代码
mvn spotless:apply
```

### 构建时的行为

**默认情况下，代码规范检查不会在构建时自动执行**，这样可以：
- 避免网络问题导致构建失败
- 加快日常开发的构建速度
- 让开发者自主选择何时进行代码规范检查

```bash
# 正常构建（不执行代码规范检查）
mvn clean install

# 构建前手动检查代码规范
mvn checkstyle:check && mvn clean install
```

## 常见问题

### 1. Checkstyle 检查失败

如果 Checkstyle 检查失败，请根据错误信息修改代码：
- 添加缺失的 Javadoc 注释
- 修复命名不规范的变量/方法
- 移除 `printStackTrace()` 调用，使用日志框架代替

### 2. Spotless 检查失败

如果 Spotless 检查失败，运行以下命令自动修复：

```bash
mvn spotless:apply
```

### 3. 跳过代码规范检查（不推荐）

由于代码规范检查默认不在构建时执行，通常不需要跳过。如果需要在 CI/CD 中启用自动检查，可以：

```bash
# 在 CI/CD 中启用 Checkstyle 检查
mvn checkstyle:check

# 在 CI/CD 中启用 Spotless 检查
mvn spotless:check
```

## IDE 集成

### IntelliJ IDEA

1. 安装 Checkstyle-IDEA 插件
2. 在设置中导入 `checkstyle/shortlink_checkstyle.xml`
3. 安装 Eclipse Code Formatter 插件
4. 导入 `format/shortlink_spotless_formatter.xml`

### Eclipse

1. 导入 `format/shortlink_spotless_formatter.xml` 作为代码格式化配置
2. 安装 Checkstyle 插件并导入配置文件

## 最佳实践

1. **提交前检查**：在提交代码前运行 `mvn validate` 确保代码符合规范
2. **自动格式化**：定期运行 `mvn spotless:apply` 保持代码格式统一
3. **持续集成**：在 CI/CD 流程中集成代码规范检查
4. **团队协作**：确保所有团队成员使用相同的代码规范配置

## 参考资料

- [Checkstyle 官方文档](https://checkstyle.sourceforge.io/)
- [Spotless 官方文档](https://github.com/diffplug/spotless)
- [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
