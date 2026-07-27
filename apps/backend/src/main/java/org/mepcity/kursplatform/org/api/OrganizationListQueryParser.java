package org.mepcity.kursplatform.org.api;

import java.text.Normalizer;
import java.util.Locale;
import org.mepcity.kursplatform.org.application.OrganizationListService;
import org.mepcity.kursplatform.org.application.OrganizationListValidationException;
import org.mepcity.kursplatform.org.domain.OrganizationListQuery;
import org.mepcity.kursplatform.org.domain.OrganizationStatus;

/** Parses and normalizes the public ORG_LIST query string without owning HTTP orchestration. */
final class OrganizationListQueryParser {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_SEARCH_LENGTH = 200;

    private OrganizationListQueryParser() {}

    static OrganizationListService.Query parse(
            String status,
            String search,
            String sort,
            String order,
            String limit,
            String cursor) {
        try {
            OrganizationStatus parsedStatus =
                    status == null ? null : OrganizationStatus.valueOf(status);
            String normalizedSearch = normalizeSearch(search);
            OrganizationListQuery.Sort parsedSort = parseSort(sort);
            OrganizationListQuery.Order parsedOrder =
                    order == null ? OrganizationListQuery.Order.ASC : OrganizationListQuery.Order.valueOf(order);
            int parsedLimit = limit == null ? DEFAULT_LIMIT : Integer.parseInt(limit);
            if (parsedLimit < 1 || parsedLimit > MAX_LIMIT) {
                throw new IllegalArgumentException("limit out of range");
            }
            boolean supplied =
                    status != null
                            || search != null
                            || sort != null
                            || order != null
                            || limit != null
                            || cursor != null;
            return new OrganizationListService.Query(
                    parsedStatus,
                    normalizedSearch,
                    parsedSort,
                    parsedOrder,
                    parsedLimit,
                    cursor,
                    supplied);
        } catch (RuntimeException exception) {
            throw new OrganizationListValidationException();
        }
    }

    private static OrganizationListQuery.Sort parseSort(String sort) {
        if (sort == null) {
            return OrganizationListQuery.Sort.NAME;
        }
        return switch (sort) {
            case "name" -> OrganizationListQuery.Sort.NAME;
            case "createdAt" -> OrganizationListQuery.Sort.CREATED_AT;
            default -> throw new IllegalArgumentException("unknown sort");
        };
    }

    static String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalized =
                Normalizer.normalize(search.trim(), Normalizer.Form.NFKC)
                        .replace('I', 'ı')
                        .replace('İ', 'i')
                        .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("search too long");
        }
        return normalized;
    }
}
