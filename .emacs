;; Set paths where add on lisp files are found
(setq load-path
       (nconc
          '(
             "C:/emacs-20.3.1/site-lisp"
	     ;; Add more directories here:
           )
           load-path))

;; Make bash the default shell
(setq binary-process-input t) 
          (setq w32-quote-process-args ?\") 
          (setq shell-file-name "bash") ;; or sh if you rename your bash executable to sh. 
          (setenv "SHELL" shell-file-name) 
          (setq explicit-shell-file-name shell-file-name) 
          (setq explicit-sh-args '("-login" "-i"))

;; fix java tabbing
(defconst cbd-java-style 
  '("java" (c-offsets-alist . ((substatement-open . 0)
                               (block-open . 0)))))

(defun cbd-java-hook ()
  (c-add-style "cbd-java" cbd-java-style t))
(add-hook 'java-mode-hook 'cbd-java-hook)
(setq-default indent-tabs-mode nil)
(custom-set-variables '(java-default-style "cbd-java"))




