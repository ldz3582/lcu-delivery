package com.lcu.delivery.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: {} {}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(Exception e) {
        String msg = "参数不合法";
        if (e instanceof MethodArgumentNotValidException ex && ex.getBindingResult().getFieldError() != null) {
            msg = ex.getBindingResult().getFieldError().getDefaultMessage();
        }
        return Result.fail(ErrorCode.PARAM, msg);
    }

    /**
     * 查询参数类型转换失败（典型场景：前端把 undefined 序列化成 "undefined" 拼进 URL）。
     * 返回 400 参数错误，而不是 500，避免把调用方问题暴露成服务端故障。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {} = {}", e.getName(), e.getValue());
        return Result.fail(ErrorCode.PARAM, "参数 " + e.getName() + " 格式不正确");
    }

    /**
     * 路径打错（含 /api/after-sales/ 这种空 id）应该回 404，别报成服务端 500。
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public Result<Void> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return Result.fail(ErrorCode.NOT_FOUND, "接口不存在：" + e.getResourcePath());
    }

    /**
     * 字段超长/超范围类数据库错误：属于**调用方传入数据不合法**，应该回 1001 而不是 500。
     * 典型：昵称/店铺名/菜品名/评价内容超过列长度，前端没做长度校验就直接提交。
     */
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,
            org.springframework.dao.DataAccessException.class})
    public Result<Void> handleDataTooLong(Exception e) {
        log.warn("数据写入被数据库拒绝（多半是字段超长/非法字符）：{}", e.getMessage());
        return Result.fail(ErrorCode.PARAM, "提交的内容过长或格式不合法，请精简后重试");
    }

    /**
     * 请求体不是合法 JSON / 缺字段：属于调用方问题，回 1001，不要 500。
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public Result<Void> handleBadBody(org.springframework.http.converter.HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return Result.fail(ErrorCode.PARAM, "请求体格式不正确，请检查 JSON");
    }

    /**
     * 上传相关：没带文件、文件超过 multipart 限额。Spring 在进 Controller 之前就会抛，
     * 必须在这里兜住，否则前端看到的是 500。
     */
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public Result<Void> handleMultipart(org.springframework.web.multipart.MultipartException e) {
        log.warn("上传被拒绝：{}", e.getMessage());
        String msg = e.getMessage() != null && e.getMessage().contains("exceed")
                ? "图片不能超过 5MB" : "请选择要上传的图片";
        return Result.fail(ErrorCode.PARAM, msg);
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public Result<Void> handleMissingPart(org.springframework.web.multipart.support.MissingServletRequestPartException e) {
        return Result.fail(ErrorCode.PARAM, "请选择要上传的图片（缺少 file 字段）");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return Result.fail(500, "服务开小差了，请稍后重试");
    }
}
