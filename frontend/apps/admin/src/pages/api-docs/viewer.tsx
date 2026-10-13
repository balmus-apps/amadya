import { client } from "@amadya/api-client";
import { useQuery } from "@tanstack/react-query";
import SwaggerUI from "swagger-ui-react";
import "swagger-ui-react/swagger-ui.css";
import { Empty } from "@/components/kit";
import { accessToken } from "@/lib/session";

/** Swagger UI's own "Authorize" dialog only invites pasting tokens: requests already carry the session. */
const HideAuthorize = () => ({ wrapComponents: { authorizeBtn: () => () => null } });

export default function ApiDocsViewer() {
  // Fetched with the session (the document is admin-only) and handed over as text, so Swagger UI never fetches it.
  const { data: spec, error } = useQuery({
    queryKey: ["openapi-spec"],
    queryFn: async () => {
      const res = await fetch(`${client.getConfig().baseUrl}/admin/openapi.yaml`, { headers: { Authorization: `Bearer ${await accessToken()}` } });
      if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
      return res.text();
    },
    staleTime: Infinity,
  });
  if (error) return <Empty>{String(error.message)}</Empty>;
  if (!spec) return <Empty loading />;
  return (
    // Swagger UI is styled for a light page; keep it on one in either theme.
    <div className="overflow-hidden rounded-xl border bg-white text-black">
      <SwaggerUI
        spec={spec}
        plugins={[HideAuthorize]}
        docExpansion="none"
        tryItOutEnabled
        requestInterceptor={async (request) => {
          // A fresh token for every call (the session refreshes it shortly before it expires).
          request.headers.Authorization = `Bearer ${await accessToken()}`;
          request.headers["X-Amadya-App"] = "admin";
          return request;
        }}
      />
    </div>
  );
}
