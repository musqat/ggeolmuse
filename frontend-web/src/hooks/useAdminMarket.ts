import { useState, useEffect } from 'react';
import { marketAdminApi } from '@services/adminApi';
import type { Asset, CompanyOverview } from '@services/adminApi';
import { getApiErrorMessage } from '../utils/apiError';
import { getTodayString, subtractDays } from '../utils/dateUtils';
import { notify } from '@/components/common/notify';
import { useAdminAction } from './useAdminAction';

export const useAdminMarket = () => {
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState<Asset[]>([]);
  const [preview, setPreview] = useState<CompanyOverview | null>(null);
  const [assets, setAssets] = useState<Asset[]>([]);
  const { loading, error, setError, run } = useAdminAction();

  // Pagination state
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [sortBy, setSortBy] = useState('symbol');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

  const [searchActive, setSearchActive] = useState(false);

  // 상장 목록과 상장폐지 목록을 오간다
  const [listActive, setListActive] = useState(true);

  // 보유 종목 검색 (DB) — 등록된 종목에서 키워드 매칭
  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      clearSearch();
      return;
    }
    await run({
      call: async () => {
        const results = await marketAdminApi.searchAssets(searchKeyword);
        setSearchResults(results);
        setSearchActive(true);
      },
      failure: () => '검색에 실패했습니다.',
    });
  };

  // 검색 해제 → 전체 목록으로 복귀
  const clearSearch = () => {
    setSearchKeyword('');
    setSearchResults([]);
    setSearchActive(false);
  };

  // 외부 조회(preview) 초기화
  const resetPreview = () => setPreview(null);

  // 심볼 미리보기
  const handlePreview = async (symbol: string) => {
    await run({
      call: async () => setPreview(await marketAdminApi.previewAsset(symbol)),
      failure: () => '미리보기 조회에 실패했습니다.',
    });
  };

  // 심볼 추가
  const handleAddAsset = async () => {
    if (!preview) return;

    await run({
      call: async () => {
        await marketAdminApi.createAsset({
          symbol: preview.symbol,
          name: preview.name,
          country: preview.country,
          currency: preview.currency,
          assetType: preview.assetType,
          collectData: true,
          fromDate: subtractDays(getTodayString(), 365),
          toDate: getTodayString(),
          includeDividends: true,
        });

        notify.success('심볼이 추가되었고 데이터 수집이 시작되었습니다.');
        setPreview(null);
        loadAssets();
      },
      failure: () => '심볼 추가에 실패했습니다.',
    });
  };

  // 선택 상태
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const toggleOne = (symbol: string) =>
    setSelected(prev => {
      const next = new Set(prev);
      if (next.has(symbol)) {
        next.delete(symbol);
      } else {
        next.add(symbol);
      }
      return next;
    });

  const toggleAll = (symbols: string[]) =>
    setSelected(prev =>
      prev.size === symbols.length ? new Set() : new Set(symbols)
    );

  // 일괄 삭제
  const handleBulkDelete = async () => {
    if (selected.size === 0) return;
    await run({
      confirm: { text: `${selected.size}개 종목을 비활성화하시겠습니까?`, danger: true },
      call: async () => {
        const res = await marketAdminApi.bulkDeleteAssets([...selected]);
        notify.success(res.message);
        setSelected(new Set());
        await loadAssets();
      },
      failure: () => '일괄 삭제에 실패했습니다.',
    });
  };

  // 상장폐지 처리한 종목을 되돌린다. 되돌리면 수집 대상에 다시 들어간다
  const handleRestoreAsset = async (symbol: string) => {
    await run({
      confirm: { text: `${symbol}을(를) 상장 상태로 되돌립니다. 계속할까요?` },
      call: async () => {
        await marketAdminApi.restoreAsset(symbol);
        loadAssets();
      },
      failure: () => '심볼 복구에 실패했습니다.',
    });
  };

  // 상장 목록과 상장폐지 목록을 바꾼다
  const handleListActiveChange = (active: boolean) => {
    setListActive(active);
    setCurrentPage(0);
    setSelected(new Set());
    loadAssets(0, active);
  };

  // 심볼 삭제
  const handleDeleteAsset = async (symbol: string) => {
    await run({
      confirm: { text: `${symbol}을(를) 삭제하시겠습니까?`, danger: true },
      call: async () => {
        await marketAdminApi.deleteAsset(symbol);
        notify.success('심볼이 삭제되었습니다.');
        loadAssets();
      },
      failure: () => '심볼 삭제에 실패했습니다.',
    });
  };

  // 종목 이름 수정 (잘못 수집된 회사명 교정)
  const handleUpdateName = async (symbol: string, name: string) => {
    const trimmed = name.trim();
    if (!trimmed) {
      setError('이름은 비워둘 수 없습니다.');
      return false;
    }
    return run({
      call: async () => {
        await marketAdminApi.updateAssetName(symbol, trimmed);
        // 검색 결과에 떠 있는 경우 즉시 반영, 전체 목록은 재로드
        setSearchResults(prev =>
          prev.map(a => (a.symbol === symbol ? { ...a, name: trimmed } : a))
        );
        await loadAssets();
      },
      failure: (err) => getApiErrorMessage(err, '이름 수정에 실패했습니다.'),
    });
  };

  // 전체 심볼 목록 로드 (가격, 최신 데이터 날짜 포함, 페이지네이션)
  const loadAssets = async (page: number = currentPage, active: boolean = listActive) => {
    const safePage = typeof page === 'number' && isFinite(page) ? page : 0;
    await run({
      call: async () => {
        const data = await marketAdminApi.getAllAssetSummaries(
          safePage,
          pageSize,
          sortBy,
          sortDirection,
          active
        );
        setAssets(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
        setCurrentPage(typeof data.number === 'number' ? data.number : safePage);
      },
      failure: () => '심볼 목록 조회에 실패했습니다.',
    });
  };

  // 페이지 변경
  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
    setSelected(new Set());
    loadAssets(newPage);
  };

  // 페이지 크기 변경. 목록은 아래 effect 가 새 크기로 받는다
  const handlePageSizeChange = (newSize: number) => {
    setPageSize(newSize);
    setCurrentPage(0);
  };

  // 정렬 변경
  const handleSortChange = (field: string, direction: 'asc' | 'desc') => {
    setSortBy(field);
    setSortDirection(direction);
    setCurrentPage(0);
  };

  // 가격 업데이트
  const handleUpdatePrice = async (symbol: string) => {
    await run({
      confirm: { text: `${symbol}의 가격 데이터를 업데이트하시겠습니까?` },
      call: async () => {
        await marketAdminApi.updateAssetPrice(symbol);
        notify.success('가격 데이터가 업데이트되었습니다.');
        loadAssets();
      },
      failure: (err) => getApiErrorMessage(err, '가격 업데이트에 실패했습니다.'),
    });
  };

  // 시가총액 업데이트
  const handleUpdateMarketCap = async (symbol: string) => {
    await run({
      confirm: { text: `${symbol}의 시가총액을 업데이트하시겠습니까?` },
      call: async () => {
        await marketAdminApi.updateAssetMarketCap(symbol);
        notify.success('시가총액이 업데이트되었습니다.');
        loadAssets();
      },
      failure: (err) => getApiErrorMessage(err, '시가총액 업데이트에 실패했습니다.'),
    });
  };

  // 전체 가격 업데이트
  const handleUpdateAllPrices = async () => {
    await run({
      confirm: { text: '모든 종목의 가격 데이터를 업데이트하시겠습니까? 시간이 다소 걸릴 수 있습니다.' },
      call: async () => {
        await marketAdminApi.updateAllPrices();
        notify.success('전체 가격 데이터 업데이트가 백그라운드에서 시작되었습니다.');
      },
      failure: (err) => getApiErrorMessage(err, '전체 가격 업데이트에 실패했습니다.'),
    });
  };

  // 전체 시가총액 업데이트
  const handleUpdateAllMarketCaps = async () => {
    await run({
      confirm: { text: '모든 종목의 시가총액을 업데이트하시겠습니까? 시간이 다소 걸릴 수 있습니다.' },
      call: async () => {
        await marketAdminApi.updateAllMarketCaps();
        notify.success('전체 시가총액 업데이트가 백그라운드에서 시작되었습니다.');
      },
      failure: (err) => getApiErrorMessage(err, '전체 시가총액 업데이트에 실패했습니다.'),
    });
  };

  // 신규 상장 종목 수집
  const handleCollectNewSymbols = async () => {
    await run({
      confirm: { text: '신규 상장 종목을 지금 받아오시겠습니까? 기존 종목은 건드리지 않습니다.' },
      call: async () => {
        await marketAdminApi.collectNewSymbols();
        notify.success('신규 종목 수집이 백그라운드에서 시작되었습니다. 잠시 뒤 목록을 새로고침해 주세요.');
      },
      failure: (err) => getApiErrorMessage(err, '신규 종목 수집에 실패했습니다.'),
    });
  };


  // 처음 열 때와 정렬 · 페이지 크기가 바뀔 때 첫 페이지부터 받는다.
  // 처음 불러오기를 따로 두면 첫 렌더에서 이것과 같이 돌아 같은 요청이 두 번 간다
  useEffect(() => {
    loadAssets(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sortBy, sortDirection, pageSize]);

  return {
    searchKeyword,
    setSearchKeyword,
    searchResults,
    searchActive,
    clearSearch,
    preview,
    resetPreview,
    assets,
    loading,
    error,
    handleSearch,
    handlePreview,
    handleAddAsset,
    handleDeleteAsset,
    handleRestoreAsset,
    listActive,
    handleListActiveChange,
    handleUpdateName,
    handleBulkDelete,
    handleUpdatePrice,
    handleUpdateMarketCap,
    handleUpdateAllPrices,
    handleUpdateAllMarketCaps,
    handleCollectNewSymbols,
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
