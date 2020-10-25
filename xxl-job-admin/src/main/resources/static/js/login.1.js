$(function(){

	// input iCheck
    $('input').iCheck({
      checkboxClass: 'icheckbox_square-blue',
      radioClass: 'iradio_square-blue',
      increaseArea: '20%' // optional
    });
    
	// login Form Valid
	var loginFormValid = $("#loginForm").validate({
		errorElement : 'span',  
        errorClass : 'help-block',
        focusInvalid : true,  
        rules : {  
        	userName : {  
        		required : true ,
                minlength: 4,
                maxlength: 18
            },  
            password : {  
            	required : true ,
                minlength: 4,
                maxlength: 18
            } 
        }, 
        messages : {  
        	userName : {  
                required  : I18n.login_username_empty,
                minlength : I18n.login_username_lt_4
            },
            password : {
            	required  : I18n.login_password_empty  ,
                minlength : I18n.login_password_lt_4
                /*,maxlength:"登录密码不应超过18位"*/
            }
        }, 
		highlight : function(element) {  
            $(element).closest('.form-group').addClass('has-error');  
        },
        success : function(label) {  
            label.closest('.form-group').removeClass('has-error');  
            label.remove();  
        },
        errorPlacement : function(error, element) {  
            element.parent('div').append(error);  
        },
        submitHandler : function(form) {
		    // 使用 Ajax 提交 login 表单，登录成功则将 Cookie 保存在浏览器中，然后使用 js 改变 location
            // 登录失败，则使用 js 弹出提示框，避免重新请求
			$.post(base_url + "/login", $("#loginForm").serialize(), function(data, status) {
				if (data.code == "200") {
                    layer.msg( I18n.login_success );
                    // 设置一定延迟后跳转到首页
                    setTimeout(function(){
                        window.location.href = base_url;
                    }, 20000);
				} else {
                    layer.open({
                        title: I18n.system_tips,
                        btn: [ I18n.system_ok ],
                        content: (data.msg || I18n.login_fail ),
                        icon: '2'
                    });
				}
			});
		}
	});
});