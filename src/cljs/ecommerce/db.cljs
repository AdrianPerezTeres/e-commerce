(ns ecommerce.db)

(def default-db
  {:current-route nil
   :auth          {:token    nil
                   :user     nil
                   :loading  false
                   :error    nil}
   :products      {:items   []
                   :loading false
                   :error   nil}
   :cart          {:items []}
   :orders        {:items   []
                   :loading false}
   :search        {:query    ""
                   :category ""}
   :ui            {:modal nil
                   :notification nil}})
