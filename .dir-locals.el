;;; package:

((clojure-mode   
         (cider-clojure-cli-global-options     . "-A:dev")
         (cider-ns-refresh-before-fn           . "dev-extras/suspend")
         (cider-ns-refresh-after-fn            . "dev-extras/resume")
         (cider-repl-init-code                 . ("(dev)"))
         (cider-repl-require-ns-on-set         . t)
         (cider-preferred-build-tool           . clojure-cli)
         (cider-redirect-server-output-to-repl . t)
         (cider-repl-display-help-banner       . nil)
         (clojure-toplevel-inside-comment-form . t)

))
