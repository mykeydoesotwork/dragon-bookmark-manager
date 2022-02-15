(ns dragon-bookmark-manager.macros
    (:require
        [clojure.pprint]))

(defmacro unless [arg & body]
  `(if (not ~arg)
     (do ~@body)))

(defmacro def-let
  "like let, but binds the expressions globally."
  [bindings & more]
  (let [let-expr (macroexpand `(let ~bindings))
        names-values (partition 2 (second let-expr))
        defs   (map #(cons 'def %) names-values)]
    (concat (list 'do) defs more)))

(defmacro testmacro [testprint]
  (println "testprint: " testprint))


;; (defmacro create-widget [] 
;;   (def t "hi"))

;;(macroexpand-1 '(macros/create-widget))


