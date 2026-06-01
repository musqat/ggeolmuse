import { useState, useEffect } from 'react';
import { marketAdminApi } from '@services/adminApi';
import type { Asset, CompanyOverview } from '@services/adminApi';

export const useAdminMarket = () => {
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState<Asset[]>([]);
  const [preview, setPreview] = useState<CompanyOverview | null>(null);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Pagination state
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [sortBy, setSortBy] = useState('symbol');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

  // 심볼 검색
  const handleSearch = async () => {
    if (!searchKeyword.trim()) return;

    setLoading(true);
    setError(null);
    try {
      const results = await marketAdminApi.searchAssets(searchKeyword);
      setSearchResults(results);
    } catch (err) {
      setError('검색에 실패했습니다.');
      console.error('Search failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 심볼 미리보기
  const handlePreview = async (symbol: string) => {
    setLoading(true);
    setError(null);
    try {
      const data = await marketAdminApi.previewAsset(symbol);
      setPreview(data);
    } catch (err) {
      setError('미리보기 조회에 실패했습니다.');
      console.error('Preview failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 심볼 추가
  const handleAddAsset = async () => {
    if (!preview) return;

    setLoading(true);
    setError(null);
    try {
      await marketAdminApi.createAsset({
        symbol: preview.symbol,
        name: preview.name,
        country: preview.country,
        currency: preview.currency,
        assetType: preview.assetType,
        collectData: true,
        fromDate: new Date(Date.now() - 365 * 24 * 60 * 60 * 1000)
          .toISOString()
          .split('T')[0],
        toDate: new Date().toISOString().split('T')[0],
        includeDividends: true,
      });

      alert('심볼이 추가되었고 데이터 수집이 시작되었습니다.');
      setPreview(null);
      loadAssets();
    } catch (err) {
      setError('심볼 추가에 실패했습니다.');
      console.error('Add asset failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 선택 상태
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const toggleOne = (symbol: string) =>
    setSelected(prev => {
      const next = new Set(prev);
      next.has(symbol) ? next.delete(symbol) : next.add(symbol);
      return next;
    });

  const toggleAll = (symbols: string[]) =>
    setSelected(prev =>
      prev.size === symbols.length ? new Set() : new Set(symbols)
    );

  // 일괄 삭제
  const handleBulkDelete = async () => {
    if (selected.size === 0) return;
    if (!confirm(`${selected.size}개 종목을 비활성화하시겠습니까?`)) return;
    setLoading(true);
    setError(null);
    try {
      const res = await marketAdminApi.bulkDeleteAssets([...selected]);
      alert(res.message);
      setSelected(new Set());
      await loadAssets();
    } catch (err) {
      setError('일괄 삭제에 실패했습니다.');
      console.error('Bulk delete failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 심볼 삭제
  const handleDeleteAsset = async (symbol: string) => {
    if (!confirm(`${symbol}을(를) 삭제하시겠습니까?`)) return;

    setLoading(true);
    setError(null);
    try {
      await marketAdminApi.deleteAsset(symbol);
      alert('심볼이 삭제되었습니다.');
      loadAssets();
    } catch (err) {
      setError('심볼 삭제에 실패했습니다.');
      console.error('Delete failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 전체 심볼 목록 로드 (가격, 최신 데이터 날짜 포함, 페이지네이션)
  const loadAssets = async (page: number = currentPage) => {
    const safePage = typeof page === 'number' && isFinite(page) ? page : 0;
    setLoading(true);
    setError(null);
    try {
      const data = await marketAdminApi.getAllAssetSummaries(
        safePage,
        pageSize,
        sortBy,
        sortDirection
      );
      setAssets(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
      setCurrentPage(typeof data.number === 'number' ? data.number : safePage);
    } catch (err) {
      setError('심볼 목록 조회에 실패했습니다.');
      console.error('Load assets failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 페이지 변경
  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
    setSelected(new Set());
    loadAssets(newPage);
  };

  // 페이지 크기 변경
  const handlePageSizeChange = (newSize: number) => {
    setPageSize(newSize);
    setCurrentPage(0);
    loadAssets(0);
  };

  // 정렬 변경
  const handleSortChange = (field: string, direction: 'asc' | 'desc') => {
    setSortBy(field);
    setSortDirection(direction);
    setCurrentPage(0);
  };

  // 가격 업데이트
  const handleUpdatePrice = async (symbol: string) => {
    if (!confirm(`${symbol}의 가격 데이터를 업데이트하시겠습니까?`)) return;

    setLoading(true);
    setError(null);
    try {
      await marketAdminApi.updateAssetPrice(symbol);
      alert('가격 데이터가 업데이트되었습니다.');
      loadAssets();
    } catch (err: any) {
      const message = err.response?.data?.message || '가격 업데이트에 실패했습니다.';
      setError(message);
      console.error('Update price failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 시가총액 업데이트
  const handleUpdateMarketCap = async (symbol: string) => {
    if (!confirm(`${symbol}의 시가총액을 업데이트하시겠습니까?`)) return;

    setLoading(true);
    setError(null);
    try {
      await marketAdminApi.updateAssetMarketCap(symbol);
      alert('시가총액이 업데이트되었습니다.');
      loadAssets();
    } catch (err: any) {
      const message = err.response?.data?.message || '시가총액 업데이트에 실패했습니다.';
      setError(message);
      console.error('Update market cap failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 전체 가격 업데이트
  const handleUpdateAllPrices = async () => {
    if (!confirm('모든 종목의 가격 데이터를 업데이트하시겠습니까? 시간이 다소 걸릴 수 있습니다.')) return;

    setLoading(true);
    setError(null);
    try {
      await marketAdminApi.updateAllPrices();
      alert('전체 가격 데이터 업데이트가 백그라운드에서 시작되었습니다.');
    } catch (err: any) {
      const message = err.response?.data?.message || '전체 가격 업데이트에 실패했습니다.';
      setError(message);
      console.error('Update all prices failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 전체 시가총액 업데이트
  const handleUpdateAllMarketCaps = async () => {
    if (!confirm('모든 종목의 시가총액을 업데이트하시겠습니까? 시간이 다소 걸릴 수 있습니다.')) return;

    setLoading(true);
    setError(null);
    try {
      await marketAdminApi.updateAllMarketCaps();
      alert('전체 시가총액 업데이트가 백그라운드에서 시작되었습니다.');
    } catch (err: any) {
      const message = err.response?.data?.message || '전체 시가총액 업데이트에 실패했습니다.';
      setError(message);
      console.error('Update all market caps failed:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAssets();
  }, []);

  // 정렬이 변경되면 데이터 다시 로드
  useEffect(() => {
    if (sortBy && sortDirection) {
      loadAssets(0);
    }
  }, [sortBy, sortDirection]);

  return {
    searchKeyword,
    setSearchKeyword,
    searchResults,
    preview,
    assets,
    loading,
    error,
    handleSearch,
    handlePreview,
    handleAddAsset,
    handleDeleteAsset,
    handleBulkDelete,
    handleUpdatePrice,
    handleUpdateMarketCap,
    handleUpdateAllPrices,
    handleUpdateAllMarketCaps,
    loadAssets,
    // Pagination
    currentPage,
    pageSize,
    totalPages,
    totalElements,
    sortBy,
    sortDirection,
    handlePageChange,
    handlePageSizeChange,
    handleSortChange,
    // Bulk selection
    selected,
    toggleOne,
    toggleAll,
  };
};
